package io.github.tomsadeh.puki.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/** A walk through the paths a child takes: start, the hub, a few games, My Day and the canvas. */
@RunWith(AndroidJUnit4::class)
class PukiProfile {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun walk() = rule.collect(
        packageName = InstrumentationRegistry.getArguments().getString("targetAppId") ?: "io.github.tomsadeh.puki.bench",
        maxIterations = 3,
        stableIterations = 2,
        includeInStartupProfile = true
    ) {
        pressHome()
        // (not startActivityAndWait: this library can't read Android 16's answer to it)
        device.executeShellCommand("am start -W -n $packageName/io.github.tomsadeh.puki.MainActivity")
        // A fresh install starts with the grown-up's setup: down to its button, and on to the hub
        if (device.wait(Until.hasObject(By.text(LETS_PLAY)), 2_000) != true) {
            device.findObject(By.scrollable(true))?.scrollUntil(Direction.DOWN, Until.findObject(By.text(LETS_PLAY)))
        }
        device.findObject(By.text(LETS_PLAY))?.click()
        Thread.sleep(4_000)  // the hub settles

        val w = device.displayWidth
        val h = device.displayHeight
        fun visit(x: Float, y: Float) {
            device.click((w * x).toInt(), (h * y).toInt())
            Thread.sleep(4_000)  // the game's prompt and its idle motion
            repeat(5) { i -> device.click(w * (i + 1) / 6, h * 2 / 3) }  // a few taps in play
            Thread.sleep(1_000)
            device.pressBack()
            Thread.sleep(1_500)
        }
        // By place on the map, which is the same in both languages
        for ((x, y) in ISLANDS) visit(x, y)
        // My Day: into the house, and a few of its rooms
        device.click((w * MY_DAY.first).toInt(), (h * MY_DAY.second).toInt())
        Thread.sleep(2_500)
        for ((x, y) in ROOMS) visit(x, y)
        device.pressBack()
        Thread.sleep(1_500)

        device.click(device.displayWidth - 80, device.displayHeight - 80)  // the canvas, in its corner
        Thread.sleep(3_000)
        device.swipe(device.displayWidth / 3, device.displayHeight / 3, device.displayWidth * 2 / 3, device.displayHeight / 2, 40)
        Thread.sleep(1_000)
        device.pressBack()
    }

    private companion object {
        val LETS_PLAY: Pattern = Pattern.compile("יאללה.*|Let's play.*")
        val ISLANDS = listOf(0.14f to 0.5f, 0.33f to 0.42f, 0.52f to 0.53f, 0.71f to 0.43f, 0.88f to 0.37f, 0.91f to 0.635f, 0.77f to 0.81f)
        val MY_DAY = 0.385f to 0.8f
        val ROOMS = listOf(0.78f to 0.35f, 0.59f to 0.35f, 0.41f to 0.35f, 0.22f to 0.35f, 0.78f to 0.75f, 0.59f to 0.75f, 0.41f to 0.75f, 0.22f to 0.75f)
    }
}
