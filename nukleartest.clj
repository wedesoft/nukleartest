(ns nukleartest
    (:import [org.lwjgl.glfw GLFW]
             [org.lwjgl.opengl GL GL11 GL14 GL15 GL20 GL30]
             [org.lwjgl.nuklear Nuklear NkContext NkAllocator NkRect NkUserFont NkPluginAllocI NkPluginFreeI NkConvertConfig
              NkDrawVertexLayoutElement NkDrawVertexLayoutElement$Buffer NkBuffer NkDrawNullTexture]
             [org.lwjgl BufferUtils PointerBuffer]
             [org.lwjgl.system MemoryUtil MemoryStack]))

(def width 640)
(def height 480)
(def buffer-initial-size (* 4 1024))
(def max-vertex-buffer (* 512 1024))
(def max-element-buffer (* 128 1024))

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
in vec2 frag_uv;
in vec4 frag_color;
out vec4 out_color;
void main()
{
  out_color = frag_color;
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

(def stack (MemoryStack/stackPush))
(def rect (NkRect/malloc stack))

(def null-texture (NkDrawNullTexture/create))  ; TODO: use white 1x1 texture
(.id (.texture null-texture) 0)
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

(while (not (GLFW/glfwWindowShouldClose window))
       (GLFW/glfwPollEvents)
       (when (Nuklear/nk_begin context "Nuklear Example" (Nuklear/nk_rect 0 0 width height rect) 0)
          (Nuklear/nk_layout_row_dynamic context 32 1)
          (swap! i inc)
          (let [p (PointerBuffer/allocateDirect 1)]
            (.put p @i)
            (.flip p)
            (Nuklear/nk_progress context p 10000 true)
            (Nuklear/nk_end context)
            (GL11/glClearColor 0.2 0.4 0.2 1.0)
            (GL11/glClear GL11/GL_COLOR_BUFFER_BIT)
            ; TODO: (GL11/glEnable GL11/GL_BLEND)
            ; TODO: (GL14/glBlendEquation GL14/GL_FUNC_ADD)
            ; TODO: (GL14/glBlendFunc GL14/GL_SRC_ALPHA GL14/GL_ONE_MINUS_SRC_ALPHA)
            (GL11/glDisable GL11/GL_CULL_FACE)
            (GL11/glDisable GL11/GL_DEPTH_TEST)
            ; TODO: (GL11/glEnable GL11/GL_SCISSOR_TEST)
            ; TODO: (GL13/glActiveTexture GL13/GL_TEXTURE0)
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

(GL20/glDeleteProgram program)

(.free (.alloc allocator))
(.free (.mfree allocator))

(GLFW/glfwTerminate)

(System/exit 0)
