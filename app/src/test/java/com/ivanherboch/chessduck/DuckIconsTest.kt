package com.ivanherboch.chessduck

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.material.icons.automirrored.rounded.Backspace as Rautomirrored_rounded_Backspace
import androidx.compose.material.icons.automirrored.rounded.Logout as Rautomirrored_rounded_Logout
import androidx.compose.material.icons.automirrored.rounded.MenuBook as Rautomirrored_rounded_MenuBook
import androidx.compose.material.icons.automirrored.rounded.Undo as Rautomirrored_rounded_Undo
import androidx.compose.material.icons.outlined.Extension as Routlined_Extension
import androidx.compose.material.icons.outlined.Keyboard as Routlined_Keyboard
import androidx.compose.material.icons.outlined.School as Routlined_School
import androidx.compose.material.icons.rounded.Analytics as Rrounded_Analytics
import androidx.compose.material.icons.rounded.Casino as Rrounded_Casino
import androidx.compose.material.icons.rounded.ChildCare as Rrounded_ChildCare
import androidx.compose.material.icons.rounded.Download as Rrounded_Download
import androidx.compose.material.icons.rounded.ErrorOutline as Rrounded_ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess as Rrounded_ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore as Rrounded_ExpandMore
import androidx.compose.material.icons.rounded.Extension as Rrounded_Extension
import androidx.compose.material.icons.rounded.Flag as Rrounded_Flag
import androidx.compose.material.icons.rounded.Folder as Rrounded_Folder
import androidx.compose.material.icons.rounded.History as Rrounded_History
import androidx.compose.material.icons.rounded.HourglassTop as Rrounded_HourglassTop
import androidx.compose.material.icons.rounded.Insights as Rrounded_Insights
import androidx.compose.material.icons.rounded.IosShare as Rrounded_IosShare
import androidx.compose.material.icons.rounded.Keyboard as Rrounded_Keyboard
import androidx.compose.material.icons.rounded.Lightbulb as Rrounded_Lightbulb
import androidx.compose.material.icons.rounded.LocalFireDepartment as Rrounded_LocalFireDepartment
import androidx.compose.material.icons.rounded.Pause as Rrounded_Pause
import androidx.compose.material.icons.rounded.People as Rrounded_People
import androidx.compose.material.icons.rounded.Replay as Rrounded_Replay
import androidx.compose.material.icons.rounded.RestartAlt as Rrounded_RestartAlt
import androidx.compose.material.icons.rounded.School as Rrounded_School
import androidx.compose.material.icons.rounded.SearchOff as Rrounded_SearchOff
import androidx.compose.material.icons.rounded.Shuffle as Rrounded_Shuffle
import androidx.compose.material.icons.rounded.SkipNext as Rrounded_SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious as Rrounded_SkipPrevious
import androidx.compose.material.icons.rounded.SmartToy as Rrounded_SmartToy
import androidx.compose.material.icons.rounded.SportsEsports as Rrounded_SportsEsports
import androidx.compose.material.icons.rounded.SportsScore as Rrounded_SportsScore
import androidx.compose.material.icons.rounded.SwapVert as Rrounded_SwapVert
import androidx.compose.material.icons.rounded.Tune as Rrounded_Tune
import androidx.compose.material.icons.rounded.Visibility as Rrounded_Visibility
import androidx.compose.material.icons.rounded.VisibilityOff as Rrounded_VisibilityOff
import com.ivanherboch.chessduck.ui.icons.Analytics
import com.ivanherboch.chessduck.ui.icons.Backspace
import com.ivanherboch.chessduck.ui.icons.Casino
import com.ivanherboch.chessduck.ui.icons.ChildCare
import com.ivanherboch.chessduck.ui.icons.Download
import com.ivanherboch.chessduck.ui.icons.ErrorOutline
import com.ivanherboch.chessduck.ui.icons.ExpandLess
import com.ivanherboch.chessduck.ui.icons.ExpandMore
import com.ivanherboch.chessduck.ui.icons.Extension
import com.ivanherboch.chessduck.ui.icons.Flag
import com.ivanherboch.chessduck.ui.icons.Folder
import com.ivanherboch.chessduck.ui.icons.History
import com.ivanherboch.chessduck.ui.icons.HourglassTop
import com.ivanherboch.chessduck.ui.icons.Insights
import com.ivanherboch.chessduck.ui.icons.IosShare
import com.ivanherboch.chessduck.ui.icons.Keyboard
import com.ivanherboch.chessduck.ui.icons.Lightbulb
import com.ivanherboch.chessduck.ui.icons.LocalFireDepartment
import com.ivanherboch.chessduck.ui.icons.Logout
import com.ivanherboch.chessduck.ui.icons.MenuBook
import com.ivanherboch.chessduck.ui.icons.Pause
import com.ivanherboch.chessduck.ui.icons.People
import com.ivanherboch.chessduck.ui.icons.Replay
import com.ivanherboch.chessduck.ui.icons.RestartAlt
import com.ivanherboch.chessduck.ui.icons.School
import com.ivanherboch.chessduck.ui.icons.SearchOff
import com.ivanherboch.chessduck.ui.icons.Shuffle
import com.ivanherboch.chessduck.ui.icons.SkipNext
import com.ivanherboch.chessduck.ui.icons.SkipPrevious
import com.ivanherboch.chessduck.ui.icons.SmartToy
import com.ivanherboch.chessduck.ui.icons.SportsEsports
import com.ivanherboch.chessduck.ui.icons.SportsScore
import com.ivanherboch.chessduck.ui.icons.SwapVert
import com.ivanherboch.chessduck.ui.icons.Tune
import com.ivanherboch.chessduck.ui.icons.Undo
import com.ivanherboch.chessduck.ui.icons.Visibility
import com.ivanherboch.chessduck.ui.icons.VisibilityOff
import org.junit.Assert.assertEquals
import org.junit.Test

/** Les icônes recopiées dans ui/icons/DuckIcons.kt sont identiques (tracés, opacités, miroir) aux icônes Material. */
class DuckIconsTest {
    private fun paths(v: ImageVector): List<VectorPath> {
        val out = ArrayList<VectorPath>()
        fun walk(g: VectorGroup) { for (i in 0 until g.size) when (val n = g[i]) { is VectorPath -> out.add(n); is VectorGroup -> walk(n) } }
        walk(v.root)
        return out
    }

    private fun check(label: String, mine: ImageVector, ref: ImageVector) {
        assertEquals(label, ref.autoMirror, mine.autoMirror)
        assertEquals(label, ref.viewportWidth, mine.viewportWidth)
        val a = paths(mine)
        val b = paths(ref)
        assertEquals(label, b.size, a.size)
        for (i in a.indices) {
            assertEquals(label, b[i].pathData, a[i].pathData)
            assertEquals(label, b[i].fillAlpha, a[i].fillAlpha)
            assertEquals(label, b[i].pathFillType, a[i].pathFillType)
        }
    }

    @Test
    fun generatedIconsMatchMaterial() {
        check("automirrored.rounded.Backspace", Icons.AutoMirrored.Rounded.Backspace, Icons.AutoMirrored.Rounded.Rautomirrored_rounded_Backspace)
        check("automirrored.rounded.Logout", Icons.AutoMirrored.Rounded.Logout, Icons.AutoMirrored.Rounded.Rautomirrored_rounded_Logout)
        check("automirrored.rounded.MenuBook", Icons.AutoMirrored.Rounded.MenuBook, Icons.AutoMirrored.Rounded.Rautomirrored_rounded_MenuBook)
        check("automirrored.rounded.Undo", Icons.AutoMirrored.Rounded.Undo, Icons.AutoMirrored.Rounded.Rautomirrored_rounded_Undo)
        check("outlined.Extension", Icons.Outlined.Extension, Icons.Outlined.Routlined_Extension)
        check("outlined.Keyboard", Icons.Outlined.Keyboard, Icons.Outlined.Routlined_Keyboard)
        check("outlined.School", Icons.Outlined.School, Icons.Outlined.Routlined_School)
        check("rounded.Analytics", Icons.Rounded.Analytics, Icons.Rounded.Rrounded_Analytics)
        check("rounded.Casino", Icons.Rounded.Casino, Icons.Rounded.Rrounded_Casino)
        check("rounded.ChildCare", Icons.Rounded.ChildCare, Icons.Rounded.Rrounded_ChildCare)
        check("rounded.Download", Icons.Rounded.Download, Icons.Rounded.Rrounded_Download)
        check("rounded.ErrorOutline", Icons.Rounded.ErrorOutline, Icons.Rounded.Rrounded_ErrorOutline)
        check("rounded.ExpandLess", Icons.Rounded.ExpandLess, Icons.Rounded.Rrounded_ExpandLess)
        check("rounded.ExpandMore", Icons.Rounded.ExpandMore, Icons.Rounded.Rrounded_ExpandMore)
        check("rounded.Extension", Icons.Rounded.Extension, Icons.Rounded.Rrounded_Extension)
        check("rounded.Flag", Icons.Rounded.Flag, Icons.Rounded.Rrounded_Flag)
        check("rounded.Folder", Icons.Rounded.Folder, Icons.Rounded.Rrounded_Folder)
        check("rounded.History", Icons.Rounded.History, Icons.Rounded.Rrounded_History)
        check("rounded.HourglassTop", Icons.Rounded.HourglassTop, Icons.Rounded.Rrounded_HourglassTop)
        check("rounded.Insights", Icons.Rounded.Insights, Icons.Rounded.Rrounded_Insights)
        check("rounded.IosShare", Icons.Rounded.IosShare, Icons.Rounded.Rrounded_IosShare)
        check("rounded.Keyboard", Icons.Rounded.Keyboard, Icons.Rounded.Rrounded_Keyboard)
        check("rounded.Lightbulb", Icons.Rounded.Lightbulb, Icons.Rounded.Rrounded_Lightbulb)
        check("rounded.LocalFireDepartment", Icons.Rounded.LocalFireDepartment, Icons.Rounded.Rrounded_LocalFireDepartment)
        check("rounded.Pause", Icons.Rounded.Pause, Icons.Rounded.Rrounded_Pause)
        check("rounded.People", Icons.Rounded.People, Icons.Rounded.Rrounded_People)
        check("rounded.Replay", Icons.Rounded.Replay, Icons.Rounded.Rrounded_Replay)
        check("rounded.RestartAlt", Icons.Rounded.RestartAlt, Icons.Rounded.Rrounded_RestartAlt)
        check("rounded.School", Icons.Rounded.School, Icons.Rounded.Rrounded_School)
        check("rounded.SearchOff", Icons.Rounded.SearchOff, Icons.Rounded.Rrounded_SearchOff)
        check("rounded.Shuffle", Icons.Rounded.Shuffle, Icons.Rounded.Rrounded_Shuffle)
        check("rounded.SkipNext", Icons.Rounded.SkipNext, Icons.Rounded.Rrounded_SkipNext)
        check("rounded.SkipPrevious", Icons.Rounded.SkipPrevious, Icons.Rounded.Rrounded_SkipPrevious)
        check("rounded.SmartToy", Icons.Rounded.SmartToy, Icons.Rounded.Rrounded_SmartToy)
        check("rounded.SportsEsports", Icons.Rounded.SportsEsports, Icons.Rounded.Rrounded_SportsEsports)
        check("rounded.SportsScore", Icons.Rounded.SportsScore, Icons.Rounded.Rrounded_SportsScore)
        check("rounded.SwapVert", Icons.Rounded.SwapVert, Icons.Rounded.Rrounded_SwapVert)
        check("rounded.Tune", Icons.Rounded.Tune, Icons.Rounded.Rrounded_Tune)
        check("rounded.Visibility", Icons.Rounded.Visibility, Icons.Rounded.Rrounded_Visibility)
        check("rounded.VisibilityOff", Icons.Rounded.VisibilityOff, Icons.Rounded.Rrounded_VisibilityOff)
    }
}
