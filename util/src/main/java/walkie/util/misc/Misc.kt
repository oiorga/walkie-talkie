package walkie.util.misc

import kotlin.random.Random

class CountDown(val max: Int) {
    private var counter: Int = 0

    val on: Boolean
        get() = (counter in 1..max)

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