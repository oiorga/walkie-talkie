package walkie.util.mesh

import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.random.nextLong

class CountDown(val max: Int) {
    private var counter: Int = 0

    val on: Boolean
        get() = (counter in 1..max)

    fun reset() = start()

    fun start() {
        counter = max
    }

    fun startRandom() {
        counter = max - Random.nextInt(max)
    }

    fun stop() {
        counter = 0
    }

    fun tick(): Int {
        if (counter > 0)
            counter --
        return counter
    }

    val value
        get() = counter
}