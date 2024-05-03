(ns nukleartest
    (:import [org.lwjgl.glfw GLFW]
             [org.lwjgl.opengl GL GL11 GL13 GL14 GL15 GL20 GL30]
             [org.lwjgl.nuklear Nuklear NkContext NkAllocator NkRect NkColor NkUserFont NkPluginAllocI NkPluginFreeI
              NkConvertConfig NkDrawVertexLayoutElement NkDrawVertexLayoutElement$Buffer NkBuffer NkDrawNullTexture
              NkTextWidthCallbackI NkQueryFontGlyphCallbackI NkHandle NkUserFontGlyph]
             [org.lwjgl BufferUtils PointerBuffer]
             [org.lwjgl.system MemoryUtil MemoryStack]
             [org.lwjgl.stb STBTruetype STBTTFontinfo STBTTPackedchar STBTTPackContext STBImageWrite STBTTAlignedQuad]))

(def width 320)
(def height 240)
(def buffer-initial-size (* 4 1024))
(def max-vertex-buffer (* 512 1024))
(def max-element-buffer (* 128 1024))
(def font-height 18)
(def bitmap-w 512)
(def bitmap-h 512)

(def stack (MemoryStack/stackPush))

(defn make-float-buffer [data]
  (doto (BufferUtils/createFloatBuffer (count data))
        (.put data)
        (.flip)))

(GLFW/glfwInit)

(def window (GLFW/glfwCreateWindow width height "Nuklear Example" 0 0))

(GLFW/glfwMakeContextCurrent window)
(GLFW/glfwShowWindow window)
(GL/createCapabilities)
(GLFW/glfwSwapInterval 1)

(def context (NkContext/create))

(def allocator (NkAllocator/create))
(.alloc allocator (reify NkPluginAllocI (invoke [this handle old size] (MemoryUtil/nmemAllocChecked size))))
(.mfree allocator (reify NkPluginFreeI (invoke [this handle ptr] (MemoryUtil/nmemFree ptr))))

(def font (NkUserFont/create))

(def ttf-in (clojure.java.io/input-stream "FiraSans.ttf"))
(def ttf-out (java.io.ByteArrayOutputStream.))
(clojure.java.io/copy ttf-in ttf-out)
(def ttf-bytes (.toByteArray ttf-out))
(def ttf (BufferUtils/createByteBuffer (count ttf-bytes)))
(.put ttf ttf-bytes)
(.flip ttf)

(def fontinfo (STBTTFontinfo/create))
(def cdata (STBTTPackedchar/calloc 95))

(STBTruetype/stbtt_InitFont fontinfo ttf)
(def scale (STBTruetype/stbtt_ScaleForPixelHeight fontinfo font-height))

(def d (.mallocInt stack 1))
(STBTruetype/stbtt_GetFontVMetrics fontinfo nil d nil)
(def descent (* (.get d 0) scale))
(def bitmap (MemoryUtil/memAlloc (* bitmap-w bitmap-h)))
(def pc (STBTTPackContext/malloc stack))
(STBTruetype/stbtt_PackBegin pc bitmap bitmap-w bitmap-h 0 1 0)
(STBTruetype/stbtt_PackSetOversampling pc 4 4)
(STBTruetype/stbtt_PackFontRange pc ttf 0 font-height 32 cdata)
(STBTruetype/stbtt_PackEnd pc)

(def texture (MemoryUtil/memAlloc (* bitmap-w bitmap-h 4)))
(def data (byte-array (* bitmap-w bitmap-h)))
(.get bitmap data)
(def data (int-array (mapv #(bit-or (bit-shift-left % 24) 0x00FFFFFF) data)))
(.put (.asIntBuffer texture) data)
(.flip texture)

; (STBImageWrite/stbi_write_png "font.png" bitmap-w bitmap-h 4 texture (* 4 bitmap-w))

(def font-tex (GL11/glGenTextures))
(GL11/glBindTexture GL11/GL_TEXTURE_2D font-tex)
(GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 GL11/GL_RGBA8 bitmap-w bitmap-h 0 GL11/GL_RGBA GL11/GL_UNSIGNED_BYTE texture)
(GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MIN_FILTER GL11/GL_LINEAR)
(GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MAG_FILTER GL11/GL_LINEAR)
(MemoryUtil/memFree texture)
(MemoryUtil/memFree bitmap)

(.width font
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
                                 (STBTruetype/stbtt_GetCodepointHMetrics fontinfo (.get unicode 0) advance nil)
                                 (let [text-width (+ text-width (* (.get advance 0) scale))
                                       glyph-len  (Nuklear/nnk_utf_decode (+ text text-len)
                                                                          (MemoryUtil/memAddress unicode) (- len text-len))]
                                   (recur (+ text-len glyph-len) glyph-len text-width)))))]
                   (MemoryStack/stackPop)
                   result))))
(.height font font-height)
(.query font
        (reify NkQueryFontGlyphCallbackI
               (invoke [this handle font-height glyph codepoint next-codepoint]
                 (let [stack   (MemoryStack/stackPush)
                       x       (.floats stack 0.0)
                       y       (.floats stack 0.0)
                       q       (STBTTAlignedQuad/malloc stack)
                       advance (.mallocInt stack 1)]
                   (STBTruetype/stbtt_GetPackedQuad cdata bitmap-w bitmap-h (- codepoint 32) x y q false)
                   (STBTruetype/stbtt_GetCodepointHMetrics fontinfo codepoint advance nil)
                   (let [ufg (NkUserFontGlyph/create glyph)]
                     (.width ufg (- (.x1 q) (.x0 q)))
                     (.height ufg (- (.y1 q) (.y0 q)))
                     (.set (.offset ufg) (.x0 q) (+ (.y0 q) font-height descent))
                     (.xadvance ufg (* (.get advance 0) scale))
                     (.set (.uv ufg 0) (.s0 q) (.t0 q))
                     (.set (.uv ufg 1) (.s1 q) (.t1 q)))
                   (MemoryStack/stackPop)))))
(def handle (NkHandle/create))
(.id handle font-tex)
(.texture font handle)

; (Nuklear/nk_style_set_font context font)

(def cmds (NkBuffer/create))

(Nuklear/nk_init context allocator font)

(Nuklear/nk_buffer_init cmds allocator buffer-initial-size)

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
(GL20/glShaderSource vertex-shader vertex-source)
(GL20/glCompileShader vertex-shader)
(when (zero? (GL20/glGetShaderi vertex-shader GL20/GL_COMPILE_STATUS))
  (println (GL20/glGetShaderInfoLog vertex-shader 1024))
  (System/exit 1))

(def fragment-shader (GL20/glCreateShader GL20/GL_FRAGMENT_SHADER))
(GL20/glShaderSource fragment-shader fragment-source)
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

(def projection (GL20/glGetUniformLocation program "projection"))
(def position (GL20/glGetAttribLocation program "position"))
(def texcoord (GL20/glGetAttribLocation program "texcoord"))
(def color (GL20/glGetAttribLocation program "color"))

(GL20/glUniform1i (GL20/glGetAttribLocation program "tex") 0)

(def vbo (GL15/glGenBuffers))
(def ebo (GL15/glGenBuffers))
(def vao (GL30/glGenVertexArrays))

(GL30/glBindVertexArray vao)
(GL15/glBindBuffer GL15/GL_ARRAY_BUFFER vbo)
(GL15/glBindBuffer GL15/GL_ELEMENT_ARRAY_BUFFER ebo)

(GL20/glEnableVertexAttribArray position)
(GL20/glEnableVertexAttribArray texcoord)
(GL20/glEnableVertexAttribArray color)

(GL20/glVertexAttribPointer position 2 GL11/GL_FLOAT false 20 0)
(GL20/glVertexAttribPointer texcoord 2 GL11/GL_FLOAT false 20 8)
(GL20/glVertexAttribPointer color 4 GL11/GL_UNSIGNED_BYTE true 20 16)

(def rect (NkRect/malloc stack))
(def rgb (NkColor/malloc stack))

(def null-tex (GL11/glGenTextures))
(GL11/glBindTexture GL11/GL_TEXTURE_2D null-tex)
(def buffer (BufferUtils/createIntBuffer 1))
(.put buffer (int-array [0xFFFFFFFF]))
(.flip buffer)
(GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 GL11/GL_RGBA8 1 1 0 GL11/GL_RGBA GL11/GL_UNSIGNED_BYTE buffer)
(GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MIN_FILTER GL11/GL_NEAREST)
(GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MAG_FILTER GL11/GL_NEAREST)

(def null-texture (NkDrawNullTexture/create))
(.id (.texture null-texture) null-tex)
(.set (.uv null-texture) 0.5 0.5)

(def vertex-layout (NkDrawVertexLayoutElement/malloc 4))
(-> vertex-layout (.position 0) (.attribute Nuklear/NK_VERTEX_POSITION) (.format Nuklear/NK_FORMAT_FLOAT) (.offset 0))
(-> vertex-layout (.position 1) (.attribute Nuklear/NK_VERTEX_TEXCOORD) (.format Nuklear/NK_FORMAT_FLOAT) (.offset 8))
(-> vertex-layout (.position 2) (.attribute Nuklear/NK_VERTEX_COLOR) (.format Nuklear/NK_FORMAT_R8G8B8A8) (.offset 16))
(-> vertex-layout (.position 3) (.attribute Nuklear/NK_VERTEX_ATTRIBUTE_COUNT) (.format Nuklear/NK_FORMAT_COUNT) (.offset 0))
(.flip vertex-layout)

(def config (NkConvertConfig/calloc stack))

(doto config
      (.vertex_layout vertex-layout)
      (.vertex_size 20)
      (.vertex_alignment 4)
      (.tex_null null-texture)
      (.circle_segment_count 22)
      (.curve_segment_count 22)
      (.arc_segment_count 22)
      (.global_alpha 1.0)
      (.shape_AA Nuklear/NK_ANTI_ALIASING_ON)
      (.line_AA Nuklear/NK_ANTI_ALIASING_ON))

(def i (atom 0))

(def p (PointerBuffer/allocateDirect 1))

(while (not (GLFW/glfwWindowShouldClose window))
       (GLFW/glfwPollEvents)
       (when (Nuklear/nk_begin context "Nuklear Example" (Nuklear/nk_rect 0 0 width height rect) 0)
          (let [canvas (Nuklear/nk_window_get_canvas context)]
            (.put p (swap! i #(mod (inc %) 100))) (.flip p)
            (Nuklear/nk_layout_row_dynamic context 32 1)
            (Nuklear/nk_progress context p 100 false)
            (Nuklear/nk_layout_row_dynamic context 120 1)
            (Nuklear/nk_widget rect context)
            (Nuklear/nk_fill_rect canvas rect 2 (Nuklear/nk_rgb 127 63 63 rgb))
            (Nuklear/nk_fill_circle canvas (Nuklear/nk_rect (+ (.x rect) (- (/ (.w rect) 2) 32))
                                                            (+ (.y rect) (- (/ (.h rect) 2) 32)) 64 64 rect)
                                    (Nuklear/nk_rgb 63 63 127 rgb))
            (Nuklear/nk_layout_row_dynamic context 32 1)
            (Nuklear/nk_label context "Nuklear" Nuklear/NK_TEXT_LEFT)
            (Nuklear/nk_layout_row_dynamic context 32 2)
            (Nuklear/nk_check_label context "Checked" true)
            (Nuklear/nk_check_label context "Not Checked" false)
            (Nuklear/nk_end context)
            (GL11/glClearColor 0.2 0.4 0.2 1.0)
            (GL11/glClear GL11/GL_COLOR_BUFFER_BIT)
            (GL11/glEnable GL11/GL_BLEND)
            (GL14/glBlendEquation GL14/GL_FUNC_ADD)
            (GL14/glBlendFunc GL14/GL_SRC_ALPHA GL14/GL_ONE_MINUS_SRC_ALPHA)
            (GL11/glDisable GL11/GL_CULL_FACE)
            (GL11/glDisable GL11/GL_DEPTH_TEST)
            (GL11/glEnable GL11/GL_SCISSOR_TEST)
            (GL13/glActiveTexture GL13/GL_TEXTURE0)
            (GL20/glUseProgram program)
            (GL20/glUniformMatrix4fv projection false (make-float-buffer (float-array [(/ 2.0 width) 0.0 0.0 0.0,
                                                                                       0.0 (/ -2.0 height) 0.0 0.0,
                                                                                       0.0 0.0 -1.0 0.0,
                                                                                       -1.0 1.0 0.0 1.0])))
            (GL11/glViewport 0 0 width height)
            (GL15/glBufferData GL15/GL_ARRAY_BUFFER max-vertex-buffer GL15/GL_STREAM_DRAW)
            (GL15/glBufferData GL15/GL_ELEMENT_ARRAY_BUFFER max-element-buffer GL15/GL_STREAM_DRAW)
            (let [vertices (GL15/glMapBuffer GL15/GL_ARRAY_BUFFER GL15/GL_WRITE_ONLY max-vertex-buffer nil)
                  elements (GL15/glMapBuffer GL15/GL_ELEMENT_ARRAY_BUFFER GL15/GL_WRITE_ONLY max-element-buffer nil)
                  stack    (MemoryStack/stackPush)
                  vbuf     (NkBuffer/malloc stack)
                  ebuf     (NkBuffer/malloc stack)]
              (Nuklear/nk_buffer_init_fixed vbuf vertices)
              (Nuklear/nk_buffer_init_fixed ebuf elements)
              (Nuklear/nk_convert context cmds vbuf ebuf config)
              (GL15/glUnmapBuffer GL15/GL_ELEMENT_ARRAY_BUFFER)
              (GL15/glUnmapBuffer GL15/GL_ARRAY_BUFFER)
              (let [cmd    (atom (Nuklear/nk__draw_begin context cmds))
                    offset (atom 0)]
                (while @cmd
                       (when (not (zero? (.elem_count @cmd)))
                         (GL11/glBindTexture GL11/GL_TEXTURE_2D (.id (.texture @cmd)))
                         (let [clip-rect (.clip_rect @cmd)]
                           (GL11/glScissor (int (.x clip-rect))
                                           (int (- height (int (+ (.y clip-rect) (.h clip-rect)))))
                                           (int (.w clip-rect))
                                           (int (.h clip-rect))))
                         (GL11/glDrawElements GL11/GL_TRIANGLES (.elem_count @cmd) GL11/GL_UNSIGNED_SHORT @offset)
                         (swap! offset + (* 2 (.elem_count @cmd))))
                       (reset! cmd (Nuklear/nk__draw_next @cmd cmds context))))
              (Nuklear/nk_clear context)
              (Nuklear/nk_buffer_clear cmds)
              (GLFW/glfwSwapBuffers window)
              (MemoryStack/stackPop)))))

(Nuklear/nk_free context)

(GL30/glBindVertexArray 0)
(GL15/glBindBuffer GL15/GL_ARRAY_BUFFER 0)
(GL15/glBindBuffer GL15/GL_ELEMENT_ARRAY_BUFFER 0)

(GL30/glDeleteVertexArrays vao)
(GL15/glDeleteBuffers ebo)
(GL15/glDeleteBuffers vbo)

(GL11/glDeleteTextures null-tex)

(GL20/glDeleteProgram program)

(.free (.alloc allocator))
(.free (.mfree allocator))

(GLFW/glfwTerminate)

(System/exit 0)
