(ns adtech.macros.core
  (:require [clojure.string :as string]
            [instaparse.core :refer [parser]]))

(def ^:dynamic *registered-filters*
  {:url #(java.net.URLEncoder/encode %)
   :upper string/upper-case
   :lower string/lower-case
   :capitalize string/capitalize})

(def ^:dynamic *filters* [:url])

(def path-parser
  (parser
   "S = FS? { E <'.'> } E BU?
   WS = <#'\\s+'>
   BU = <WS> S
   FS = <'|'> { K <','> } K <WS>
   <K> = #'[-_:0-9a-zA-Z/*?<>]+'
   <E> = K | <'('> S <')'>"))

(defn- macro-spec?
  [t]
  (and (vector? t) (> (count t) 0) (= :S (nth t 0))))

(defn- backup-spec?
  [t]
  (and (vector? t) (> (count t) 0) (= :BU (nth t 0))))

(defn- filters-spec?
  [t]
  (and (vector? t) (> (count t) 0) (= :FS (nth t 0))))

(declare ^:private make-macro-spec)

(defn- make-backup-spec
  [coll]
  (make-macro-spec (nth coll 1)))

(defn- make-filters-spec
  [coll]
  (map keyword (rest coll)))

(defn- make-macro-spec
  [t]
  (if (macro-spec? t)
    (loop [t (rest t) spec {}]
      (if (seq t)
        (let [el (first t)]
          (cond (backup-spec? el)
                (recur (rest t) (assoc spec :backup (make-backup-spec el)))
                (filters-spec? el)
                (recur (rest t) (assoc spec :filters (make-filters-spec el)))
                (macro-spec? el)
                (recur (rest t) (update-in spec [:path] (fnil conj []) (make-macro-spec el)))
                (string? el)
                (recur (rest t) (update-in spec [:path] (fnil conj []) el))))
        spec))))

(declare ^:private replace-macro)

(defn- cleanse
  [v]
  (cond (nil? v)
        nil
        (string? v)
        v
        (or (keyword? v) (symbol? v))
        (name v)
        (not (or (map? v) (sequential? v)))
        (str v)
        :else
        nil))

(defn- filter-value
  [fs v]
  (reduce (fn [v f] (f v))
          v
          (->> (concat fs *filters*)
               (map #(or (*registered-filters* %) %)))))

(defn- finish-value
  [filters coll value missing backup nested?]
  (if-let [v (cleanse value)]
    (filter-value filters v)
    (if backup
      (replace-macro (assoc backup :filters filters) coll missing nested?)
      (filter-value filters missing))))

(defn- replace-macro
  [{:keys [filters backup path] :as tree} coll missing nested?]
  (loop [path path subcoll coll]
    (if (seq path)
      (let [raw-el (first path)
            el (if (map? raw-el)
                 (binding [*filters* nil] (replace-macro raw-el coll nil true))
                 raw-el)
            val (cond (nil? el)
                      nil
                      (map? subcoll)
                      (or (get subcoll (keyword el))
                          (get subcoll el))
                      (sequential? subcoll)
                      (let [idx (try (Integer/parseInt el)
                                     (catch NumberFormatException e
                                       nil))]
                        (when (contains? subcoll idx)
                          (nth subcoll idx)))
                      :else
                      nil)]
        (if val
          (recur (rest path) val)
          (finish-value filters coll val missing backup nested?)))
      (finish-value filters coll subcoll missing backup nested?))))

(def ^:dynamic *registered-styles*
  {:shell
   {:pat #"\$\{\s*([^{}]+?)\s*\}" :val second}
   :mustache
   {:pat #"\{\{\s*([^{}]+?)\s*\}\}" :val second}
   :comb
   {:pat #"<%=\s*([^%]+?)\s*%>" :val second}
   :dots
   {:pat #"\.\.\.\s*([^{}]+?)\s*\.\.\." :val second}
   :pparens
   {:pat #"\(\(\s*([^{}]+?)\s*\)\)" :val second}})

(def ^:dynamic *style* :shell)

(defn render
  ([s coll] (render s coll ""))
  ([s coll missing]
   (let [{extract-val :val pattern :pat}
         (get *registered-styles* *style*)]
     (string/replace
            s
            pattern
            (fn [match]
              (-> (path-parser (extract-val match))
                  make-macro-spec
                  (replace-macro coll missing false)))))))

(defn render* [s coll {:keys [missing style filters]
                       :or {missing "" style *style* filters *filters*}}]
  (binding [*style* style
            *filters* filters]
    (render s coll missing)))
