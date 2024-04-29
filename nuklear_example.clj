(ns nuklear-example
    (:import [org.lwjgl.glfw GLFW]
             [org.lwjgl.opengl GL GL11]
             [org.lwjgl.nuklear Nuklear NkContext NkAllocator NkPluginAllocI NkColor NkRect NkUserFont]
             [org.lwjgl BufferUtils PointerBuffer]
             [org.lwjgl.system MemoryUtil MemoryStack]))

(GLFW/glfwInit)

(defn make-byte-buffer
  [data]
  (doto (BufferUtils/createByteBuffer (count data))
    (.put ^bytes data)
    (.flip)))

(def window (GLFW/glfwCreateWindow 640 480 "Nuklear Example" 0 0))

(GLFW/glfwMakeContextCurrent window)
(GLFW/glfwShowWindow window)
(GL/createCapabilities)
; (GLFW/glfwSwapInterval 1)

(def context (NkContext/create))

(def stack (MemoryStack/stackPush))
(def cur (.mallocLong stack 1))
(.put cur 0 50)
(println (.get cur 0))
(def rect (NkRect/malloc stack))
(def font (NkUserFont/create))

(def memory (byte-array 1000000))

(def buffer (make-byte-buffer memory))

(Nuklear/nk_init_fixed context buffer font)

(while (not (GLFW/glfwWindowShouldClose window))
       (GLFW/glfwPollEvents)
       (when (Nuklear/nk_begin context "Nuklear Example" (Nuklear/nk_rect 0 0 640 480 rect) 0)
          (Nuklear/nk_layout_row_dynamic context 128 1)
          (let [p (PointerBuffer/allocateDirect 1)]
            (.put p (MemoryUtil/memAddress cur))
            (.flip p)
            (println (Nuklear/nk_progress context p 100 true))
            (Nuklear/nk_end context)
            (GLFW/glfwSwapBuffers window))))

(Nuklear/nk_free context)

(GLFW/glfwTerminate)

(System/exit 0)
