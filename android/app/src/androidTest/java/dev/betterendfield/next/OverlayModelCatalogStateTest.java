package dev.betterendfield.next;

import org.json.JSONArray;
import org.json.JSONObject;

public final class OverlayModelCatalogStateTest {
    private static void assertTrue(boolean value) {
        if (!value) throw new AssertionError("Expected true");
    }
    private static void assertFalse(boolean value) {
        if (value) throw new AssertionError("Expected false");
    }
    private static void assertEquals(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    static void run() throws Exception {
        OverlayModelCatalogState state = new OverlayModelCatalogState();
        assertFalse(state.hasCatalog());
        assertFalse(state.canWrite());
        state.beginRead(1);
        state.fail(1, "设置桥暂时不可用");
        assertTrue(state.hasError());
        assertFalse(state.hasCatalog());
        assertFalse(state.canWrite());

        String catalog = new JSONArray().put(new JSONObject()
                .put("generation", "generation-1")
                .put("name", "测试模型")
                .put("bem_minor", 4)
                .put("target_kind", "weapon")
                .put("target_id", "weapon-1")
                .put("resource_keys", new JSONArray().put("android-arm64:weapon")))
                .toString();
        state.beginRead(2);
        assertTrue(state.succeed(2, catalog, "revision-2"));
        assertTrue(state.canWrite());
        assertEquals(catalog, state.index());
        state.beginRead(3);
        state.fail(3, "读取失败");
        assertEquals(catalog, state.index());
        assertFalse(state.canWrite());
        assertEquals("", state.revision());

        state.beginRead(4);
        assertFalse(state.succeed(2, "[]", "old-revision"));
        state.succeed(4, "{}", "invalid-revision");
        assertEquals(catalog, state.index());
        assertTrue(state.hasError());
        state.beginRead(5);
        state.succeed(5, "[]", "revision-5");
        assertTrue(state.canWrite());
        assertEquals("[]", state.index());
    }
}
