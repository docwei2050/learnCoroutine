package com.docwei.learncoroutine.sequence

import kotlin.coroutines.*

/**
 * Created by liwk on 2020/9/13.
 *
 * 协程创建
 * 启动
 * 反复挂起
 * 反复恢复
 */
interface Generator<T> {
    operator fun iterator(): Iterator<T>
}

class GeneratorImpl<T>(
    private val block: suspend GeneratorScope<T>.(T) -> Unit,
    private val parameter: T
) : Generator<T> {
    override fun iterator(): Iterator<T> {
        return GeneratorIterator(block, parameter)
    }
}

sealed class State {
    class NotReady(val continuation: Continuation<Unit>) : State()
    class Ready<T>(val continuation: Continuation<Unit>, val nextValue: T) : State()
    object Done : State()
}

class GeneratorIterator<T>(
    private val block: suspend GeneratorScope<T>.(T) -> Unit,
    private val paramter: T
) : GeneratorScope<T>, Iterator<T>, Continuation<Any?> {
    override val context: CoroutineContext = EmptyCoroutineContext
    private var state: State

    init {
        val coroutineBlock: suspend GeneratorScope<T>.() -> Unit =
            {
                block(paramter)
            }
        //本身已经创建了一个协程
        val start = coroutineBlock.createCoroutine(this, this)
        state = State.NotReady(start)
    }

    //suspendCoroutine 获取到当前协程并且挂起协程
    override suspend fun yield(value: T) = suspendCoroutine<Unit> { continuation ->
        println("挂起--》" + value + "----" + continuation.toString())
        state = when (state) {
            is State.NotReady -> State.Ready(continuation, value)
            is State.Ready<*> -> throw IllegalStateException("cannot yield a value while ready")
            State.Done -> throw IllegalStateException("cannot yield a value while done")
        }

    }

    private fun resume() {
        println("恢复")
        when (val currentState = state) {
            is State.NotReady -> {
                currentState.continuation.resume(Unit)
            }
        }
    }

    override fun hasNext(): Boolean {
        resume()
        return state != State.Done
    }

    override fun next(): T {
        return when (val currentState = state) {
            is State.NotReady -> {
                resume()
                return next()
            }
            is State.Ready<*> -> {
                state = State.NotReady(currentState.continuation)
                (currentState as State.Ready<T>).nextValue
            }
            State.Done -> throw IndexOutOfBoundsException("Not value left")
        }
    }

    override fun resumeWith(result: Result<Any?>) {
        state = State.Done
        result.getOrThrow()
    }
}


fun <T> generator(block: suspend GeneratorScope<T>.(T) -> Unit): (T) -> Generator<T> {
    return { parameter: T ->
        println("parameter-->" + parameter)
        GeneratorImpl(block, parameter)
    }
}

fun main() {
    val nums = generator { start: Int ->
        for (i in 0..5) {
            yield(start + i)
        }
    }
    val seq = nums(10)
    //in 约定其实就是iterator
    for (j in seq) {
        println(j)
    }
}

//表示以GeneratorScope为Receiver的拓展挂起函数内部都只能调用自己的挂起函数
@RestrictsSuspension
interface GeneratorScope<T> {
    suspend fun yield(value: T)
}