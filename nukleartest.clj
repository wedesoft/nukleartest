(ns nukleartest
    (:import [org.lwjgl BufferUtils PointerBuffer]
             [org.lwjgl.glfw GLFW GLFWCursorPosCallbackI GLFWMouseButtonCallbackI GLFWScrollCallbackI GLFWCharCallbackI
              GLFWKeyCallbackI]
             [org.lwjgl.opengl GL GL11 GL13 GL14 GL15 GL20 GL30]
             [org.lwjgl.nuklear Nuklear NkDrawVertexLayoutElement NkDrawVertexLayoutElement$Buffer NkDrawNullTexture NkAllocator
              NkContext NkPluginAllocI NkPluginFreeI NkUserFont NkBuffer NkConvertConfig NkRect NkHandle NkTextWidthCallbackI
              NkQueryFontGlyphCallbackI NkUserFontGlyph NkVec2 NkImage NkColor NkColor$Buffer NkPluginFilter NkPluginFilterI
              NkPluginCopyI NkPluginPasteI]
             [org.lwjgl.system MemoryUtil MemoryStack]
             [org.lwjgl.stb STBTruetype STBTTFontinfo STBTTPackedchar STBTTPackedchar$Buffer STBTTPackContext STBImageWrite
              STBTTAlignedQuad STBImage]))

(set! *unchecked-math* true)
(set! *warn-on-reflection* true)

(def width 640)
(def height 640)
(def buffer-initial-size (* 4 1024))
(def max-vertex-buffer (* 512 1024))
(def max-element-buffer (* 128 1024))
(def font-height 18)
(def bitmap-w 512)
(def bitmap-h 512)


(def stack (MemoryStack/stackPush))

(GLFW/glfwInit)

(GLFW/glfwDefaultWindowHints)
(GLFW/glfwWindowHint GLFW/GLFW_RESIZABLE GLFW/GLFW_FALSE)
(def window (GLFW/glfwCreateWindow ^long width ^long height "Nuklear Example" 0 0))

(GLFW/glfwMakeContextCurrent window)
(GLFW/glfwShowWindow window)
(GL/createCapabilities)
(GLFW/glfwSwapInterval 1)

(def vertex-source
"#version 410 core
uniform mat4 projection;
in vec2 position;
in vec2 texcoord;
in vec4 color;
out vec2 frag_uv;
out vec4 frag_color;
void main()
{
  frag_uv = texcoord;
  frag_color = color;
  gl_Position = projection * vec4(position, 0, 1);
}")

(def fragment-source
"#version 410 core
uniform sampler2D tex;
in vec2 frag_uv;
in vec4 frag_color;
out vec4 out_color;
void main()
{
  out_color = frag_color * texture(tex, frag_uv);
}")

(def program (GL20/glCreateProgram))

(def vertex-shader (GL20/glCreateShader GL20/GL_VERTEX_SHADER))
(GL20/glShaderSource ^long vertex-shader ^String vertex-source)
(GL20/glCompileShader vertex-shader)
(when (zero? (GL20/glGetShaderi vertex-shader GL20/GL_COMPILE_STATUS))
  (println (GL20/glGetShaderInfoLog vertex-shader 1024))
  (System/exit 1))

(def fragment-shader (GL20/glCreateShader GL20/GL_FRAGMENT_SHADER))
(GL20/glShaderSource ^long fragment-shader ^String fragment-source)
(GL20/glCompileShader fragment-shader)
(when (zero? (GL20/glGetShaderi fragment-shader GL20/GL_COMPILE_STATUS))
  (println (GL20/glGetShaderInfoLog fragment-shader 1024))
  (System/exit 1))

(GL20/glAttachShader program vertex-shader)
(GL20/glAttachShader program fragment-shader)
(GL20/glLinkProgram program)
(when (zero? (GL20/glGetProgrami program GL20/GL_LINK_STATUS))
  (println (GL20/glGetProgramInfoLog program 1024))
  (System/exit 1))
(GL20/glDeleteShader vertex-shader)
(GL20/glDeleteShader fragment-shader)

(GL20/glUseProgram program)

(GL11/glEnable GL11/GL_BLEND)
(GL14/glBlendEquation GL14/GL_FUNC_ADD)
(GL14/glBlendFunc GL14/GL_SRC_ALPHA GL14/GL_ONE_MINUS_SRC_ALPHA)
(GL11/glDisable GL11/GL_CULL_FACE)
(GL11/glDisable GL11/GL_DEPTH_TEST)
(GL11/glEnable GL11/GL_SCISSOR_TEST)
(GL13/glActiveTexture GL13/GL_TEXTURE0)

(def projection (GL20/glGetUniformLocation ^long program "projection"))
(def buffer (BufferUtils/createFloatBuffer 16))
(.put ^java.nio.DirectFloatBufferU buffer
      (float-array [(/ 2.0 width) 0.0 0.0 0.0,
                    0.0 (/ -2.0 height) 0.0 0.0,
                    0.0 0.0 -1.0 0.0,
                    -1.0 1.0 0.0 1.0]))
(.flip ^java.nio.DirectFloatBufferU buffer)
(GL20/glUniformMatrix4fv ^long projection false ^java.nio.DirectFloatBufferU buffer)

(def position (GL20/glGetAttribLocation ^long program "position"))
(def texcoord (GL20/glGetAttribLocation ^long program "texcoord"))
(def color (GL20/glGetAttribLocation ^long program "color"))

(def vbo (GL15/glGenBuffers))
(def ebo (GL15/glGenBuffers))
(def vao (GL30/glGenVertexArrays))

(GL30/glBindVertexArray vao)
(GL15/glBindBuffer GL15/GL_ARRAY_BUFFER vbo)
(GL15/glBindBuffer GL15/GL_ELEMENT_ARRAY_BUFFER ebo)

(GL20/glEnableVertexAttribArray position)
(GL20/glEnableVertexAttribArray texcoord)
(GL20/glEnableVertexAttribArray color)

(GL20/glVertexAttribPointer ^long position 2 GL11/GL_FLOAT false 20 0)
(GL20/glVertexAttribPointer ^long texcoord 2 GL11/GL_FLOAT false 20 8)
(GL20/glVertexAttribPointer ^long color 4 GL11/GL_UNSIGNED_BYTE true 20 16)

(GL15/glBufferData GL15/GL_ARRAY_BUFFER ^long max-vertex-buffer GL15/GL_STREAM_DRAW)
(GL15/glBufferData GL15/GL_ELEMENT_ARRAY_BUFFER ^long max-element-buffer GL15/GL_STREAM_DRAW)

(set! *warn-on-reflection* false)

(def vertex-layout (NkDrawVertexLayoutElement/malloc 4))
(-> vertex-layout (.position 0) (.attribute Nuklear/NK_VERTEX_POSITION) (.format Nuklear/NK_FORMAT_FLOAT) (.offset 0))
(-> vertex-layout (.position 1) (.attribute Nuklear/NK_VERTEX_TEXCOORD) (.format Nuklear/NK_FORMAT_FLOAT) (.offset 8))
(-> vertex-layout (.position 2) (.attribute Nuklear/NK_VERTEX_COLOR) (.format Nuklear/NK_FORMAT_R8G8B8A8) (.offset 16))
(-> vertex-layout (.position 3) (.attribute Nuklear/NK_VERTEX_ATTRIBUTE_COUNT) (.format Nuklear/NK_FORMAT_COUNT) (.offset 0))
(.flip vertex-layout)

(set! *warn-on-reflection* true)

(def null-tex (GL11/glGenTextures))
(GL11/glBindTexture GL11/GL_TEXTURE_2D null-tex)
(def buffer (BufferUtils/createIntBuffer 1))
(.put ^java.nio.DirectIntBufferU buffer (int-array [0xFFFFFFFF]))
(.flip ^java.nio.DirectIntBufferU buffer)
(GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 GL11/GL_RGBA8 1 1 0 GL11/GL_RGBA GL11/GL_UNSIGNED_BYTE ^java.nio.DirectIntBufferU buffer)
(GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MIN_FILTER GL11/GL_NEAREST)
(GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MAG_FILTER GL11/GL_NEAREST)

(def null-texture (NkDrawNullTexture/create))
(.id (.texture ^NkDrawNullTexture null-texture) null-tex)
(.set ^NkVec2 (.uv ^NkDrawNullTexture null-texture) 0.5 0.5)

(def allocator (NkAllocator/create))
(.alloc ^NkAllocator allocator (reify NkPluginAllocI (invoke [this handle old size] (MemoryUtil/nmemAllocChecked size))))
(.mfree ^NkAllocator allocator (reify NkPluginFreeI (invoke [this handle ptr] (MemoryUtil/nmemFree ptr))))

(def context (NkContext/create))

(def font (NkUserFont/create))

(def ttf-in (clojure.java.io/input-stream "B612-Regular.ttf"))
(def ttf-out (java.io.ByteArrayOutputStream.))
(clojure.java.io/copy ttf-in ttf-out)
(def ttf-bytes (.toByteArray ^java.io.ByteArrayOutputStream ttf-out))
(def ttf (BufferUtils/createByteBuffer (count ttf-bytes)))
(.put ^java.nio.DirectByteBuffer ttf ^bytes ttf-bytes)
(.flip ^java.nio.DirectByteBuffer ttf)

(def fontinfo (STBTTFontinfo/create))
(def cdata (STBTTPackedchar/calloc 95))

(STBTruetype/stbtt_InitFont fontinfo ttf)
(def scale (STBTruetype/stbtt_ScaleForPixelHeight fontinfo font-height))

(def d (.mallocInt ^MemoryStack stack 1))
(STBTruetype/stbtt_GetFontVMetrics ^STBTTFontinfo fontinfo nil ^java.nio.DirectIntBufferU d nil)
(def descent (* (.get ^java.nio.DirectIntBufferU d 0) scale))
(def bitmap (MemoryUtil/memAlloc (* bitmap-w bitmap-h)))
(def pc (STBTTPackContext/malloc ^MemoryStack stack))
(STBTruetype/stbtt_PackBegin pc bitmap bitmap-w bitmap-h 0 1 0)
(STBTruetype/stbtt_PackSetOversampling pc 4 4)
(STBTruetype/stbtt_PackFontRange pc ttf 0 font-height 32 cdata)
(STBTruetype/stbtt_PackEnd pc)

(def texture (MemoryUtil/memAlloc (* bitmap-w bitmap-h 4)))
(def data (byte-array (* bitmap-w bitmap-h)))
(.get ^java.nio.DirectByteBuffer bitmap ^bytes data)
(def data (int-array (mapv #(bit-or (bit-shift-left % 24) 0x00FFFFFF) data)))
(def texture-int (.asIntBuffer ^java.nio.DirectByteBuffer texture))
(.put ^java.nio.DirectIntBufferU texture-int ^ints data)

; (STBImageWrite/stbi_write_png "font.png" bitmap-w bitmap-h 4 texture (* 4 bitmap-w))

(def font-tex (GL11/glGenTextures))
(GL11/glBindTexture GL11/GL_TEXTURE_2D font-tex)
(GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 GL11/GL_RGBA8 ^long bitmap-w ^long bitmap-h 0 GL11/GL_RGBA GL11/GL_UNSIGNED_BYTE
                   ^java.nio.DirectByteBuffer texture)
(GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MIN_FILTER GL11/GL_LINEAR)
(GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MAG_FILTER GL11/GL_LINEAR)
(MemoryUtil/memFree ^java.nio.DirectByteBuffer texture)
(MemoryUtil/memFree ^java.nio.DirectByteBuffer bitmap)

(def handle (NkHandle/create))
(.id ^NkHandle handle font-tex)
(.texture ^NkUserFont font ^NkHandle handle)
(.width ^NkUserFont font
        (reify NkTextWidthCallbackI
               (invoke [this handle h text len]
                 (let [stack     (MemoryStack/stackPush)
                       unicode   (.mallocInt stack 1)
                       advance   (.mallocInt stack 1)
                       glyph-len (Nuklear/nnk_utf_decode text (MemoryUtil/memAddress unicode) len)
                       result
                       (loop [text-len glyph-len glyph-len glyph-len text-width 0.0]
                             (if (or (> text-len len)
                                     (zero? glyph-len)
                                     (= (.get unicode 0) Nuklear/NK_UTF_INVALID))
                               text-width
                               (do
                                 (STBTruetype/stbtt_GetCodepointHMetrics ^STBTTFontinfo fontinfo (.get unicode 0) advance nil)
                                 (let [text-width (+ text-width (* (.get advance 0) scale))
                                       glyph-len  (Nuklear/nnk_utf_decode (+ text text-len)
                                                                          (MemoryUtil/memAddress unicode) (- len text-len))]
                                   (recur (+ text-len glyph-len) glyph-len text-width)))))]
                   (MemoryStack/stackPop)
                   result))))

(.height ^NkUserFont font font-height)

(.query ^NkUserFont font
        (reify NkQueryFontGlyphCallbackI
               (invoke [this handle font-height glyph codepoint next-codepoint]
                 (let [stack   (MemoryStack/stackPush)
                       x       (.floats stack 0.0)
                       y       (.floats stack 0.0)
                       q       (STBTTAlignedQuad/malloc stack)
                       advance (.mallocInt stack 1)]
                   (STBTruetype/stbtt_GetPackedQuad ^STBTTPackedchar$Buffer cdata ^long bitmap-w ^long bitmap-h (- codepoint 32) x y q false)
                   (STBTruetype/stbtt_GetCodepointHMetrics ^STBTTFontinfo fontinfo codepoint advance nil)
                   (let [ufg (NkUserFontGlyph/create glyph)]
                     (.width ufg (- (.x1 q) (.x0 q)))
                     (.height ufg (- (.y1 q) (.y0 q)))
                     (.set (.offset ufg) (.x0 q) (+ (.y0 q) font-height descent))
                     (.xadvance ufg (* (.get advance 0) scale))
                     (.set (.uv ufg 0) (.s0 q) (.t0 q))
                     (.set (.uv ufg 1) (.s1 q) (.t1 q)))
                   (MemoryStack/stackPop)))))

(def download-icon (NkImage/create))
(def w (int-array 1))
(def h (int-array 1))
(def c (int-array 1))
(def buffer (STBImage/stbi_load "download.png" ^ints w ^ints h ^ints c 4))
(def download-tex (GL11/glGenTextures))
(GL11/glBindTexture GL11/GL_TEXTURE_2D download-tex)
(GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MIN_FILTER GL11/GL_LINEAR)
(GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MAG_FILTER GL11/GL_LINEAR)
(GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 GL11/GL_RGBA8 ^int (aget ^ints w 0) ^int (aget ^ints h 0) 0 GL11/GL_RGBA
                   GL11/GL_UNSIGNED_BYTE ^java.nio.DirectByteBuffer buffer)
(GL11/glBindTexture GL11/GL_TEXTURE_2D 0)
(def handle (NkHandle/create))
(.id ^NkHandle handle download-tex)
(.handle ^NkImage download-icon ^NkHandle handle)

(Nuklear/nk_init context allocator font)

(def cmds (NkBuffer/create))
(Nuklear/nk_buffer_init cmds allocator buffer-initial-size)

(def config (NkConvertConfig/calloc ^MemoryStack stack))

(doto ^NkConvertConfig config
      (.vertex_layout ^NkDrawVertexLayoutElement$Buffer vertex-layout)
      (.vertex_size 20)
      (.vertex_alignment 4)
      (.tex_null ^NkDrawNullTexture null-texture)
      (.circle_segment_count 22)
      (.curve_segment_count 22)
      (.arc_segment_count 22)
      (.global_alpha 1.0)
      (.shape_AA Nuklear/NK_ANTI_ALIASING_ON)
      (.line_AA Nuklear/NK_ANTI_ALIASING_ON))

(GLFW/glfwSetCursorPosCallback
  window
  (reify GLFWCursorPosCallbackI
         (invoke [this window xpos ypos]
           (Nuklear/nk_input_motion context (int xpos) (int ypos)))))

(GLFW/glfwSetMouseButtonCallback
  window
  (reify GLFWMouseButtonCallbackI
         (invoke [this window button action mods]
           (let [stack (MemoryStack/stackPush)
                 cx    (.mallocDouble stack 1)
                 cy    (.mallocDouble stack 1)]
             (GLFW/glfwGetCursorPos window cx cy)
             (let [x        (int (.get cx 0))
                   y        (int (.get cy 0))
                   nkbutton (cond
                              (= button GLFW/GLFW_MOUSE_BUTTON_RIGHT) Nuklear/NK_BUTTON_RIGHT
                              (= button GLFW/GLFW_MOUSE_BUTTON_MIDDLE) Nuklear/NK_BUTTON_MIDDLE
                              :else Nuklear/NK_BUTTON_LEFT)]
               (Nuklear/nk_input_button context nkbutton x y (= action GLFW/GLFW_PRESS))
               (MemoryStack/stackPop))))))

(GLFW/glfwSetScrollCallback
  window
  (reify GLFWScrollCallbackI
         (invoke [this window xoffset yoffset]
           (let [stack (MemoryStack/stackPush)
                 scroll (NkVec2/malloc stack)]
             (.x scroll xoffset)
             (.y scroll yoffset)
             (Nuklear/nk_input_scroll context scroll)
             (MemoryStack/stackPop)))))

(GLFW/glfwSetCharCallback
  window
  (reify GLFWCharCallbackI
         (invoke [this window codepoint]
           (Nuklear/nk_input_unicode context codepoint))))

(GLFW/glfwSetKeyCallback
  window
  (reify GLFWKeyCallbackI
         (invoke [this window k scancode action mods]
           (let [press (or (= action GLFW/GLFW_PRESS) (= action GLFW/GLFW_REPEAT))]
             (cond
               (= k GLFW/GLFW_KEY_ESCAPE)      (GLFW/glfwSetWindowShouldClose window true)
               (= k GLFW/GLFW_KEY_DELETE)      (Nuklear/nk_input_key context Nuklear/NK_KEY_DEL press)
               (= k GLFW/GLFW_KEY_ENTER)       (Nuklear/nk_input_key context Nuklear/NK_KEY_ENTER press)
               (= k GLFW/GLFW_KEY_TAB)         (Nuklear/nk_input_key context Nuklear/NK_KEY_TAB press)
               (= k GLFW/GLFW_KEY_BACKSPACE)   (Nuklear/nk_input_key context Nuklear/NK_KEY_BACKSPACE press)
               (= k GLFW/GLFW_KEY_UP)          (Nuklear/nk_input_key context Nuklear/NK_KEY_UP press)
               (= k GLFW/GLFW_KEY_DOWN)        (Nuklear/nk_input_key context Nuklear/NK_KEY_DOWN press)
               (= k GLFW/GLFW_KEY_LEFT)        (Nuklear/nk_input_key context Nuklear/NK_KEY_LEFT press)
               (= k GLFW/GLFW_KEY_RIGHT)       (Nuklear/nk_input_key context Nuklear/NK_KEY_RIGHT press)
               (= k GLFW/GLFW_KEY_HOME)        (Nuklear/nk_input_key context Nuklear/NK_KEY_TEXT_START press)
               (= k GLFW/GLFW_KEY_END)         (Nuklear/nk_input_key context Nuklear/NK_KEY_TEXT_END press)
               (= k GLFW/GLFW_KEY_LEFT_SHIFT)  (Nuklear/nk_input_key context Nuklear/NK_KEY_SHIFT press)
               (= k GLFW/GLFW_KEY_RIGHT_SHIFT) (Nuklear/nk_input_key context Nuklear/NK_KEY_SHIFT press))
             (when (or (= mods GLFW/GLFW_MOD_CONTROL) (not press))
               (cond
                 (= k GLFW/GLFW_KEY_C)     (Nuklear/nk_input_key context Nuklear/NK_KEY_COPY press)
                 (= k GLFW/GLFW_KEY_P)     (Nuklear/nk_input_key context Nuklear/NK_KEY_PASTE press)
                 (= k GLFW/GLFW_KEY_X)     (Nuklear/nk_input_key context Nuklear/NK_KEY_CUT press)
                 (= k GLFW/GLFW_KEY_Z)     (Nuklear/nk_input_key context Nuklear/NK_KEY_TEXT_UNDO press)
                 (= k GLFW/GLFW_KEY_R)     (Nuklear/nk_input_key context Nuklear/NK_KEY_TEXT_REDO press)
                 (= k GLFW/GLFW_KEY_LEFT)  (Nuklear/nk_input_key context Nuklear/NK_KEY_TEXT_WORD_LEFT press)
                 (= k GLFW/GLFW_KEY_RIGHT) (Nuklear/nk_input_key context Nuklear/NK_KEY_TEXT_WORD_RIGHT press)
                 (= k GLFW/GLFW_KEY_B)     (Nuklear/nk_input_key context Nuklear/NK_KEY_TEXT_LINE_START press)
                 (= k GLFW/GLFW_KEY_E)     (Nuklear/nk_input_key context Nuklear/NK_KEY_TEXT_LINE_END press)))))))

(.copy (.clip ^NkContext context)
       (reify NkPluginCopyI
              (invoke [this handle text len]
                (if (not= len 0)
                  (let [stack  (MemoryStack/stackPush)
                        string (.malloc stack (inc len))]
                    (MemoryUtil/memCopy text (MemoryUtil/memAddress string) len)
                    (.put string len (byte 0))
                    (GLFW/glfwSetClipboardString ^long window string))))))

(.paste (.clip ^NkContext context)
        (reify NkPluginPasteI
               (invoke [this handle edit]
                 (let [text (GLFW/nglfwGetClipboardString window)]
                   (if (not= text 0)
                     (Nuklear/nnk_textedit_paste edit text (Nuklear/nnk_strlen text)))))))

(def rect (NkRect/malloc ^MemoryStack stack))
(def rgb (NkColor/malloc ^MemoryStack stack))

(def style-table (NkColor/malloc Nuklear/NK_COLOR_COUNT))
(.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_TEXT (Nuklear/nk_rgb 210 210 210 rgb))
(.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_WINDOW (Nuklear/nk_rgb 57 67 71 rgb))
(.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_HEADER (Nuklear/nk_rgb 51 51 56 rgb))
(.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_BORDER (Nuklear/nk_rgb 46 46 46 rgb))
(.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_BUTTON (Nuklear/nk_rgb 48 83 111 rgb))
(.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_BUTTON_HOVER (Nuklear/nk_rgb 58 93 121 rgb))
(.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_BUTTON_ACTIVE (Nuklear/nk_rgb 63 98 126 rgb))
(.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_TOGGLE (Nuklear/nk_rgb 50 58 61 rgb))
(.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_TOGGLE_HOVER (Nuklear/nk_rgb 45 53 56 rgb))
(.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_TOGGLE_CURSOR (Nuklear/nk_rgb 48 83 111 rgb))
(.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_SELECT (Nuklear/nk_rgb 57 67 61 rgb))
(.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_SELECT_ACTIVE (Nuklear/nk_rgb 48 83 111 rgb))
(.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_SLIDER (Nuklear/nk_rgb 50 58 61 rgb))
(.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_SLIDER_CURSOR (Nuklear/nk_rgb 48 83 111 rgb))
(.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_SLIDER_CURSOR_HOVER (Nuklear/nk_rgb 53 88 116 rgb))
(.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_SLIDER_CURSOR_ACTIVE (Nuklear/nk_rgb 58 93 121 rgb))
(.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_PROPERTY (Nuklear/nk_rgb 50 58 61 rgb))
(.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_EDIT (Nuklear/nk_rgb 50 58 61 rgb))
(.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_EDIT_CURSOR (Nuklear/nk_rgb 210 210 210 rgb))
(.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_COMBO (Nuklear/nk_rgb 50 58 61 rgb))
(.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_CHART (Nuklear/nk_rgb 50 58 61 rgb))
(.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_CHART_COLOR (Nuklear/nk_rgb 48 83 111 rgb))
(.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_CHART_COLOR_HIGHLIGHT (Nuklear/nk_rgb 255 0 0 rgb))
(.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_SCROLLBAR (Nuklear/nk_rgb 50 58 61 rgb))
(.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_SCROLLBAR_CURSOR (Nuklear/nk_rgb 48 83 111 rgb))
(.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_SCROLLBAR_CURSOR_HOVER (Nuklear/nk_rgb 53 88 116 rgb))
(.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_SCROLLBAR_CURSOR_ACTIVE (Nuklear/nk_rgb 58 93 121 rgb))
(.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_TAB_HEADER (Nuklear/nk_rgb 48 83 111 rgb))
(Nuklear/nk_style_from_table context style-table)

(def menu-size (NkVec2/create))
(.x ^NkVec2 menu-size 80)
(.y ^NkVec2 menu-size 40)

(def combo-size (NkVec2/create))
(.x ^NkVec2 combo-size 320)
(.y ^NkVec2 combo-size 120)

(def no-padding (NkVec2/create))
(.x ^NkVec2 no-padding 0)
(.y ^NkVec2 no-padding 0)

(def progress (PointerBuffer/allocateDirect 1))
(def increment (atom 1))
(def option (atom :easy))
(def flip (atom false))
(def crop (atom false))
(def compression (.put (BufferUtils/createIntBuffer 1) 0 20))
(def quality (.put (BufferUtils/createFloatBuffer 1) 0 (float 5.0)))
(def combo-items (mapv #(str "test" (inc %)) (range 10)))
(def selected (atom (first combo-items)))
(def text (BufferUtils/createByteBuffer 256))
(def text-len (int-array [0]))
(def text-filter (NkPluginFilter/create (reify NkPluginFilterI (invoke [this edit unicode] (Nuklear/nnk_filter_ascii edit unicode)))))
(def slider (BufferUtils/createIntBuffer 1))
(.put ^java.nio.DirectIntBufferU slider 0 50)

(while (not (GLFW/glfwWindowShouldClose window))
       (Nuklear/nk_input_begin context)
       (GLFW/glfwPollEvents)
       (Nuklear/nk_input_end context)
       (when (Nuklear/nk_begin ^NkContext context "Nuklear Example" (Nuklear/nk_rect 0 0 width height rect) 0)
         (Nuklear/nk_menubar_begin context)
         (Nuklear/nk_layout_row_static context 40 40 1)
         (when (Nuklear/nk_menu_begin_label ^NkContext context "Main" Nuklear/NK_TEXT_LEFT ^NkVec2 menu-size)
           (Nuklear/nk_layout_row_dynamic context 32 1)
           (if (Nuklear/nk_menu_item_label ^NkContext context "Exit" Nuklear/NK_TEXT_LEFT)
             (GLFW/glfwSetWindowShouldClose window true))
           (Nuklear/nk_menu_end context))
         (Nuklear/nk_menubar_end context)
         (let [canvas (Nuklear/nk_window_get_canvas context)]
           (Nuklear/nk_layout_row_dynamic context 120 1)
           (if (Nuklear/nk_widget_is_mouse_clicked context Nuklear/NK_BUTTON_LEFT)
             (println "Widget clicked"))
           (let [color (if (Nuklear/nk_widget_is_hovered context)
                         (Nuklear/nk_rgb 255 127 127 rgb)
                         (Nuklear/nk_rgb 255 101 101 rgb))]
             (Nuklear/nk_widget rect context)
             (Nuklear/nk_fill_rect canvas rect 2 color)
             (Nuklear/nk_fill_circle canvas (Nuklear/nk_rect (+ (.x ^NkRect rect) (- (/ (.w ^NkRect rect) 2) 32))
                                                             (+ (.y ^NkRect rect) (- (/ (.h ^NkRect rect) 2) 32)) 64 64 rect)
                                     (Nuklear/nk_rgb 127 255 127 rgb))))
         (.put ^PointerBuffer progress 0 ^long (mod (+ (.get ^PointerBuffer progress 0) @increment) 100))
         (Nuklear/nk_layout_row_dynamic context 32 1)
         (Nuklear/nk_progress context progress 100 true)
         (Nuklear/nk_layout_row_dynamic context 32 2)
         (if (Nuklear/nk_button_label ^NkContext context "Start")
           (reset! increment 1))
         (if (Nuklear/nk_button_label ^NkContext context "Stop")
           (reset! increment 0))
         (Nuklear/nk_layout_row_dynamic ^NkContext context 32 3)
         (if (Nuklear/nk_option_label ^NkContext context "easy" (= @option :easy))
           (reset! option :easy))
         (if (Nuklear/nk_option_label ^NkContext context "intermediate" (= @option :intermediate))
           (reset! option :intermediate))
         (if (Nuklear/nk_option_label ^NkContext context "hard" (= @option :hard))
           (reset! option :hard))
         (Nuklear/nk_layout_row_dynamic ^NkContext context 32 2)
         (reset! flip (Nuklear/nk_check_label ^NkContext context "Flip" ^boolean @flip))
         (reset! crop (Nuklear/nk_check_label ^NkContext context "Crop" ^boolean @crop))
         (Nuklear/nk_layout_row_dynamic ^NkContext context 86 1)
         (Nuklear/nk_group_begin_titled ^NkContext context "settings" "Settings" (bit-or Nuklear/NK_WINDOW_TITLE Nuklear/NK_WINDOW_BORDER))
         (Nuklear/nk_layout_row_dynamic ^NkContext context 32 1)
         (Nuklear/nk_property_int ^NkContext context "Compression:" 0 ^java.nio.DirectIntBufferU compression 100 10 (float 1))
         (Nuklear/nk_property_float ^NkContext context "Quality:" (float 0.0) ^java.nio.DirectFloatBufferU quality (float 10.0)
                                    (float 1.0) (float 0.01))
         (Nuklear/nk_group_end context)
         (Nuklear/nk_layout_row_dynamic ^NkContext context 32 14)
         (Nuklear/nk_button_symbol ^NkContext context Nuklear/NK_SYMBOL_RECT_SOLID)
         (Nuklear/nk_button_symbol ^NkContext context Nuklear/NK_SYMBOL_RECT_OUTLINE)
         (Nuklear/nk_button_symbol ^NkContext context Nuklear/NK_SYMBOL_TRIANGLE_UP)
         (Nuklear/nk_button_symbol ^NkContext context Nuklear/NK_SYMBOL_TRIANGLE_DOWN)
         (Nuklear/nk_button_symbol ^NkContext context Nuklear/NK_SYMBOL_TRIANGLE_LEFT)
         (Nuklear/nk_button_symbol ^NkContext context Nuklear/NK_SYMBOL_TRIANGLE_RIGHT)
         (Nuklear/nk_button_symbol ^NkContext context Nuklear/NK_SYMBOL_CIRCLE_SOLID)
         (Nuklear/nk_button_symbol ^NkContext context Nuklear/NK_SYMBOL_CIRCLE_OUTLINE)
         (Nuklear/nk_button_symbol ^NkContext context Nuklear/NK_SYMBOL_MAX)
         (Nuklear/nk_button_symbol ^NkContext context Nuklear/NK_SYMBOL_X)
         (Nuklear/nk_button_symbol ^NkContext context Nuklear/NK_SYMBOL_PLUS)
         (Nuklear/nk_button_symbol ^NkContext context Nuklear/NK_SYMBOL_MINUS)
         (Nuklear/nk_button_symbol ^NkContext context Nuklear/NK_SYMBOL_UNDERSCORE)
         (if (Nuklear/nk_button_image ^NkContext context download-icon)
           (println "Download"))
         (Nuklear/nk_layout_row_dynamic ^NkContext context 32 1)
         (when (Nuklear/nk_combo_begin_label ^NkContext context ^String @selected ^NkVec2 combo-size)
           (Nuklear/nk_layout_row_dynamic ^NkContext context 32 1)
           (doseq [item combo-items]
                  (if (Nuklear/nk_combo_item_text ^NkContext context ^String item Nuklear/NK_TEXT_LEFT)
                    (reset! selected item)))
           (Nuklear/nk_combo_end context))
         (Nuklear/nk_edit_string ^NkContext context Nuklear/NK_EDIT_FIELD ^java.nio.DirectByteBuffer text ^ints text-len 256
                                 ^NkPluginFilter text-filter)
         (Nuklear/nk_slider_int ^NkContext context 0 ^java.nio.DirectIntBufferU slider 100 1)
         (Nuklear/nk_layout_row_dynamic ^NkContext context 100 2)
         (Nuklear/nk_style_push_vec2 ^NkContext context (.group_padding (.window (.style ^NkContext context))) ^NkVec2 no-padding)
         (Nuklear/nk_group_begin ^NkContext context "buttons-1" Nuklear/NK_WINDOW_NO_SCROLLBAR)
         (Nuklear/nk_layout_row_dynamic ^NkContext context 26 1)
         (Nuklear/nk_button_label ^NkContext context "Button A")
         (Nuklear/nk_button_label ^NkContext context "Button B")
         (Nuklear/nk_button_label ^NkContext context "Button C")
         (Nuklear/nk_group_end context)
         (Nuklear/nk_group_begin ^NkContext context "buttons-2" Nuklear/NK_WINDOW_NO_SCROLLBAR)
         (Nuklear/nk_layout_row_dynamic ^NkContext context 41 1)
         (Nuklear/nk_button_label ^NkContext context "Button D")
         (Nuklear/nk_button_label ^NkContext context "Button E")
         (Nuklear/nk_group_end context)
         (Nuklear/nk_style_pop_vec2 context)
         (Nuklear/nk_end context))
       (GL11/glViewport 0 0 width height)
       (let [vertices (GL15/glMapBuffer GL15/GL_ARRAY_BUFFER GL15/GL_WRITE_ONLY max-vertex-buffer nil)
             elements (GL15/glMapBuffer GL15/GL_ELEMENT_ARRAY_BUFFER GL15/GL_WRITE_ONLY max-element-buffer nil)
             stack    (MemoryStack/stackPush)
             vbuf     (NkBuffer/malloc stack)
             ebuf     (NkBuffer/malloc stack)]
         (Nuklear/nk_buffer_init_fixed vbuf vertices)
         (Nuklear/nk_buffer_init_fixed ebuf elements)
         (Nuklear/nk_convert ^NkContext context cmds vbuf ebuf config)
         (GL15/glUnmapBuffer GL15/GL_ELEMENT_ARRAY_BUFFER)
         (GL15/glUnmapBuffer GL15/GL_ARRAY_BUFFER)
         (loop [cmd (Nuklear/nk__draw_begin ^NkContext context cmds) offset 0]
               (when cmd
                 (when (not (zero? (.elem_count cmd)))
                   (GL11/glBindTexture GL11/GL_TEXTURE_2D (.id (.texture cmd)))
                   (let [clip-rect (.clip_rect cmd)]
                     (GL11/glScissor (int (.x clip-rect))
                                     (int (- height (int (+ (.y clip-rect) (.h clip-rect)))))
                                     (int (.w clip-rect))
                                     (int (.h clip-rect))))
                   (GL11/glDrawElements GL11/GL_TRIANGLES (.elem_count cmd) GL11/GL_UNSIGNED_SHORT offset))
                 (recur (Nuklear/nk__draw_next cmd cmds context) (+ offset (* 2 (.elem_count cmd))))))
         (Nuklear/nk_clear context)
         (Nuklear/nk_buffer_clear cmds)
         (GLFW/glfwSwapBuffers window)
         (MemoryStack/stackPop)))

(Nuklear/nk_free context)

(.free (.alloc ^NkAllocator allocator))
(.free (.mfree ^NkAllocator allocator))

(GL30/glBindVertexArray 0)
(GL15/glBindBuffer GL15/GL_ARRAY_BUFFER 0)
(GL15/glBindBuffer GL15/GL_ELEMENT_ARRAY_BUFFER 0)

(GL15/glDeleteBuffers ^long ebo)
(GL15/glDeleteBuffers ^long vbo)
(GL30/glDeleteVertexArrays ^long vao)

(GL11/glDeleteTextures ^long download-tex)
(GL11/glDeleteTextures ^long null-tex)
(GL11/glDeleteTextures ^long font-tex)

(GL20/glDeleteProgram program)

(GLFW/glfwTerminate)

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)

(System/exit 0)
