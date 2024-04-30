import org.lwjgl.nuklear.NkTextWidthCallbackI;
import org.lwjgl.nuklear.NkUserFont;

class Test {
    public static float width(long handle, float h, long text, long len) {
        return 0.0f;
    }
    public static void main(String[] args) {
        NkUserFont font = NkUserFont.create();
        font.width(Test::width);
        System.out.println("Hello, World!"); 
    }
}
