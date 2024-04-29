(ns nuklear-example
    (:import [org.lwjgl.glfw GLFW]
             [org.lwjgl.opengl GL GL11]
             [org.lwjgl.nuklear Nuklear NkContext NkAllocator NkPluginAllocI NkColor NkRect NkUserFont]
             [org.lwjgl.system MemoryUtil MemoryStack]))

(GLFW/glfwInit)

(def window (GLFW/glfwCreateWindow 640 480 "Nuklear Example" 0 0))

(GLFW/glfwMakeContextCurrent window)
(GLFW/glfwShowWindow window)
(GL/createCapabilities)
; (GLFW/glfwSwapInterval 1)

(GL11/glClearColor 0.5 0.5 0.5 0.0)
(GL11/glClear GL11/GL_COLOR_BUFFER_BIT)
(GLFW/glfwSwapBuffers window)

(def context (NkContext/create))

(def stack (MemoryStack/stackPush))
(def rect (NkRect/malloc stack))
(def font (NkUserFont/create))
(def allocator (NkAllocator/create))

(defn- allocate [_, _, size]
  (MemoryUtil/nmemAlloc size))

(defn- deallocate [_, _, ptr]
  (MemoryUtil/nmemFree ptr))

(.alloc allocator ^java.lang.IFn allocate)
(.mfree allocator ^java.lang.IFn deallocate)

(Nuklear/nk_init context allocator font)

(while (not (GLFW/glfwWindowShouldClose window))
       (GLFW/glfwPollEvents)
       (when (Nuklear/nk_begin context "Nuklear Example" (Nuklear/nk_rect 0 0 640 480 rect) 0)
          (Nuklear/nk_layout_row_dynamic context 128 1)
          (if (Nuklear/nk_button_label context "Click me")
            (println "Button clicked!"))
          (Nuklear/nk_end context)
          (GLFW/glfwSwapBuffers window)))

(Nuklear/nk_free context)

(GLFW/glfwTerminate)

(System/exit 0)
