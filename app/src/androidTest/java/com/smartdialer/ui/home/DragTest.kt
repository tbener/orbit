package com.smartdialer.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartdialer.data.Contact
import com.smartdialer.data.LayoutMode
import com.smartdialer.data.Suggestion
import com.smartdialer.ui.theme.OrbitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DragTest {
    @get:Rule
    val rule = createComposeRule()

    private val people = listOf("Ann", "Bob", "Cat", "Dan", "Eve", "Fay", "Gus", "Hal", "Ivy")
    private val suggestions = people.map { Suggestion(Contact(it, it), null, null, "r") }

    private fun show(events: MutableList<String>) {
        rule.setContent {
            OrbitTheme {
                Box(Modifier.fillMaxSize()) {
                    OrbitField(
                        suggestions = suggestions,
                        selectedId = null,
                        hiddenId = null,
                        canLoadMore = false,
                        onGesture = { _, _, g, _ -> events += "gesture:$g" },
                        onLoadMore = {},
                        bottomInset = 100.dp,
                        mode = LayoutMode.Grid3,
                        dragEnabled = true,
                        pinnedSlots = emptyMap(),
                        onDragActive = { events += "active:$it" },
                        onDrop = { from, to -> events += "drop:$from->$to" },
                        onMoveRequest = { _, _ -> },
                        empty = {},
                    )
                }
            }
        }
    }

    @Test
    fun holdThenDragMovesTheBubbleAndDrops() {
        val events = mutableListOf<String>()
        show(events)
        rule.onNode(hasContentDescription("Bob", substring = true)).performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, 40f), delayMillis = 16)
            moveBy(Offset(-200f, 400f), delayMillis = 16)
            moveBy(Offset(0f, 200f), delayMillis = 16)
            up()
        }
        rule.waitForIdle()
        assertTrue(events.toString(), events.contains("active:true"))
        assertTrue(events.toString(), events.any { it.startsWith("drop:2->") })
    }

    @Test
    fun holdWithoutMovingIsALongPress() {
        val events = mutableListOf<String>()
        show(events)
        rule.onNode(hasContentDescription("Bob", substring = true)).performTouchInput { longClick() }
        rule.waitForIdle()
        assertEquals(listOf("active:true", "active:false", "gesture:LongPress").sorted(), events.sorted().take(3))
    }

    @Test
    fun aTapOpensTheQuickActionsAndAQuickSwipeNeverStartsADrag() {
        val events = mutableListOf<String>()
        show(events)
        rule.onNode(hasContentDescription("Bob", substring = true)).performTouchInput { click() }
        rule.waitForIdle()
        assertTrue(events.toString(), events == listOf("gesture:Tap"))
        events.clear()
        rule.onNode(hasContentDescription("Bob", substring = true)).performTouchInput { swipeDown() }
        rule.waitForIdle()
        assertTrue(events.toString(), events.none { it.startsWith("active") || it.startsWith("drop") })
    }
}
