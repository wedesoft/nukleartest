(ns nuklear-example
    (:import [org.lwjgl.glfw GLFW]
             [org.lwjgl.opengl GL GL11]
             [org.lwjgl.nuklear Nuklear NkContext NkAllocator NkRect NkUserFont NkPluginAllocI NkPluginFreeI]
             [org.lwjgl BufferUtils PointerBuffer]
             [org.lwjgl.system MemoryUtil MemoryStack]))

(GLFW/glfwInit)

(def window (GLFW/glfwCreateWindow 640 480 "Nuklear Example" 0 0))

(GLFW/glfwMakeContextCurrent window)
(GLFW/glfwShowWindow window)
(GL/createCapabilities)

(def context (NkContext/create))

(def allocator (NkAllocator/create))
(.alloc allocator (reify NkPluginAllocI (invoke [this handle old size] (MemoryUtil/nmemAllocChecked size))))
(.mfree allocator (reify NkPluginFreeI (invoke [this handle ptr] (MemoryUtil/nmemFree ptr))))

(def font (NkUserFont/create))

(Nuklear/nk_init context allocator font)

(def stack (MemoryStack/stackPush))
(def cur (.mallocInt stack 1))
(.put cur 0 50)
(println (.get cur 0))
(def rect (NkRect/malloc stack))


(while (not (GLFW/glfwWindowShouldClose window))
       (GLFW/glfwPollEvents)
       (when (Nuklear/nk_begin context "Nuklear Example" (Nuklear/nk_rect 0 0 640 480 rect) 0)
          (Nuklear/nk_layout_row_dynamic context 128 1)
          (let [p (PointerBuffer/allocateDirect 1)]
            (.put p (MemoryUtil/memAddress cur))
            (.flip p)
            (println (Nuklear/nk_progress context p 100 true))
            (Nuklear/nk_end context)
            (GL11/glClear GL11/GL_COLOR_BUFFER_BIT)
            (GLFW/glfwSwapBuffers window))))

(Nuklear/nk_free context)

(.free (.alloc allocator))
(.free (.mfree allocator))

(GLFW/glfwTerminate)

(System/exit 0)
