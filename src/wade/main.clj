(ns wade.main
  (:gen-class)
  (:require [clojure.java.io :as io]
            [cheshire.core :as json]
            [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.string :as str]))

(defn gather-vm-info [index res]
  (if-let [type (nth ["name" "cores" "memory" "storage"] index nil)]
    (do
      (print (format "choose %s: " type))
      (flush)
      (if-let [line (not-empty (first (line-seq (io/reader *in*))))]
        (gather-vm-info (inc index) (assoc res type line))
        nil))
    res))

(defn create-vm [app-dir]
  (if-let [info (gather-vm-info 0 {})]
    (let [vm-dir (fs/path app-dir (get info "name"))]
      (fs/create-dir vm-dir)
      (process/shell
       "qemu-img" "create" "-f" "qcow2"
       (fs/path vm-dir "system.qcow2") (get info "storage"))
      (fs/write-bytes
       (fs/path vm-dir "config.json")
       (.getBytes (json/generate-string info {:pretty true}))))
    (System/exit 1)))


(defn use-vm-insert-cmd []
  (if (= "insert" (nth *command-line-args* 1 nil))
    (when-let [image (nth *command-line-args* 2 nil)]
      ["-cdrom" (fs/path (fs/home) "Downloads" image)])
    nil))

(defn use-vm [name app-dir & args]
  (let [vm-dir (fs/path app-dir name)]
    (if (fs/exists? vm-dir)
      (let [config (json/parse-string
                    (str/join "\n" (fs/read-all-lines (fs/path vm-dir "config.json"))))]


        (apply process/shell
               "qemu-system-x86_64"
               "-accel" "accel=whpx,kernel-irqchip=off"
               "-smp" (get config "cores")
               "-m" (get config "memory")
               "-device" "virtio-vga-gl"
               "-display" "sdl,gl=on"
               "-drive" (format "file=%s,format=qcow2" (fs/path vm-dir "system.qcow2"))
               (use-vm-insert-cmd)))
      (println "can't find vm"))))




(defn -main [& args]
  (let [app-dir (fs/path (fs/home) "VMs")]
    (when (not (fs/exists? app-dir))
      (fs/create-dir app-dir))

    (if-let [name (nth args 0 nil)]
      (case name
        "new" (create-vm app-dir)
        (use-vm name app-dir (drop 1 args)))
      (println "provide name of vm or command"))))