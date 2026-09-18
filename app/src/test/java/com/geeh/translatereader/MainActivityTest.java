package com.geeh.translatereader;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
public class MainActivityTest {
    @Test public void coldLaunchBuildsHomeAndSettingsAndAllowsTabSwitching() {
        try (var controller = Robolectric.buildActivity(MainActivity.class).setup().visible()) {
            MainActivity activity = controller.get();
            View root = activity.findViewById(android.R.id.content);
            Button start = findButton(root, "Even 화면 연결하기");
            assertNotNull(start);
            assertTrue(start.isShown());
            Button settings = findButton(root, "설정");
            assertNotNull(settings); assertTrue(settings.performClick());
            assertFalse(start.isShown());
            assertTrue(findButton(root, "선택한 목소리 듣기").isShown());
            assertTrue(findButton(root, "통역").performClick());
            assertTrue(start.isShown());
        }
    }
    private static Button findButton(View view, String label) {
        if (view instanceof Button && ((Button)view).getText().toString().equals(label)) return (Button)view;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            Button match = findButton(group.getChildAt(i), label);
            if (match != null) return match;
        }
        return null;
    }
}
