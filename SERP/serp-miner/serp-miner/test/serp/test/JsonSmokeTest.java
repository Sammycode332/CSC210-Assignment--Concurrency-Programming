package serp.test;

import serp.json.Json;

import java.util.List;
import java.util.Map;

/** Unit tests for the hand-rolled JSON parser and its defensive accessors. */
public final class JsonSmokeTest {

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        System.out.println("JsonSmokeTest");

        // Scalars and whitespace tolerance.
        Check.equal(Json.parse("  true "), Boolean.TRUE, "parses true");
        Check.equal(Json.parse("false"), Boolean.FALSE, "parses false");
        Check.equal(Json.parse("null"), null, "parses null");
        Check.equal(Json.parse("42"), 42.0, "parses integer as double");
        Check.equal(Json.parse("-3.5e2"), -350.0, "parses exponent");
        Check.equal(Json.parse("\"hi\""), "hi", "parses string");

        // Escapes: \" \n and \u0041 ('A').
        String esc = (String) Json.parse("\"a\\\"b\\nA\\u0041\"");
        Check.equal(esc, "a\"b\nAA", "handles escapes incl. unicode");

        // Empty containers.
        Check.that(Json.arr(Json.parse("[]")).isEmpty(), "empty array");
        Check.that(Json.obj(Json.parse("{}")).isEmpty(), "empty object");

        // Nested structure.
        String doc = "{ \"a\": 1, \"b\": [true, null, \"x\"], "
                + "\"c\": { \"d\": 2.0, \"e\": \"deep\" } }";
        Map<String, Object> root = Json.obj(Json.parse(doc));
        Check.equal(root.get("a"), 1.0, "top-level number");
        List<Object> b = Json.arr(root.get("b"));
        Check.equal(b.size(), 3, "array length");
        Check.equal(b.get(0), Boolean.TRUE, "array[0] bool");
        Check.equal(b.get(1), null, "array[1] null");
        Check.equal(b.get(2), "x", "array[2] string");
        Map<String, Object> c = Json.obj(root.get("c"));
        Check.equal(Json.str(c, "e"), "deep", "nested string accessor");
        Check.equal(Json.num(c, "d"), 2, "nested numeric accessor -> int");

        // Defensive accessors on missing / wrong-typed keys.
        Check.equal(Json.str(root, "missing"), "", "missing string -> empty");
        Check.equal(Json.num(root, "missing"), 0, "missing number -> 0");
        Check.that(Json.arr(root.get("a")).isEmpty(), "arr() on non-array -> empty");
        Check.that(Json.obj(root.get("a")).isEmpty(), "obj() on non-object -> empty");

        Check.done("JsonSmokeTest");
    }

    private JsonSmokeTest() {}
}
