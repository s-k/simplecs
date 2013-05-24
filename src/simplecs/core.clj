(ns simplecs.core
  (:require [clojure.set :refer (rename-keys)]
            [clojure.core.incubator :refer (dissoc-in)]))

(defn entities-with-component
  "Returns a seq with all entities of 'ces' which
   have the component with the 'component-keyword'."
  [ces component-keyword]
  (keys (get-in ces [::components component-keyword])))

(defn has-component?
  "Returns true if the given entity
   has the component with 'component-keyword'."
  [ces entity component-keyword]
  (get-in ces [::entities entity component-keyword]))

(defn get-component
  "Returns the component with 'component-keyword'
   of the given entity."
  [ces entity component-keyword]
  (get-in ces [::components component-keyword entity]))

(defn get-in-entity
  "Similar to 'get-in'. (get-in-entity ces entity :component)
   is equivalent to (get-component ces entity :component).
   However, if a path is given,
   (get-in-entity ces entity [:component :a :b]) is equivalent
   to (get-in (get-component ces entity :component) [:a :b])"
  [ces entity path]
  (if (sequential? path)
    (let [[component-keyword & ks] path]
      (if ks
        (get-in (get-component ces entity component-keyword)
                ks)
        (get-component ces entity component-keyword)))
    (get-component ces entity path)))

(defn- update-component
  "Returns an updated CES where the component with 'component-keyword'
   of the given entity is updated using function 'f'."
  [ces entity component-keyword f & r]
  {:pre [(has-component? ces entity component-keyword)]
   :post [(:name (get-in % [::components component-keyword entity]))]}
  (apply update-in ces [::components component-keyword entity] f r))

(defn- update-in-component
  "(update-in-component ces entity [:component :a :b] f x y) is short for
   (update-component ces entity :component #(update-in % [:a :b] f x y))"
  [ces entity [component-keyword & ks] f & more]
  (if ks
    (update-component ces entity component-keyword #(apply update-in % ks f more))
    (apply update-component ces entity component-keyword f more)))

(defn update-entity
  "(update-entity ces entity component-keyword f & args) updates
   the component with the given keyword by applying the function
   'f' to it and any additional arguments.
   (update-entity ces entity [component-keyword & ks] f & args)
   updates the value under the key-path 'ks' in the component
   with the given keword by applying the function 'f' to it and
   any additional arguments."
  [ces entity keyword-or-list f & args]
  (if (sequential? keyword-or-list)
    (apply update-in-component ces entity keyword-or-list f args)
    (apply update-component ces entity keyword-or-list f args)))

(defn add-component
  "Returns an updated CES where for the given entity the
   new 'component' is added."
  [ces entity component]
  (-> ces
      (assoc-in [::components (:name component) entity] component)
      (update-in [::entities entity] #(conj % (:name component)))))

(defn remove-component
  "Returns an updated CES where the component with
   'component-keyword' is removed from the given entity."
  [ces entity component-keyword]
  (-> ces
      (dissoc-in [::components component-keyword entity])
      (update-in [::entities entity] #(disj % component-keyword))))

(defn component-keywords-for-entity
  "Returns a set of the keywords for all
   components of the given entity."
  [ces entity]
  (get-in ces [::entities entity]))

(defn add-entity
  "Returns an updated ces with a new entity
   which contains the given components."
  ([ces] (add-entity ces []))
  ([ces components]
   (let [id (::next-id ces)]
     (-> ces
         (update-in [::next-id] inc)
         (assoc ::last-id id)
         (assoc-in [::entities id] #{})
         (#(reduce (fn [ces component] (add-component ces id component))
                   %
                   components))))))

(defn remove-all-components-of-entity
  "Removes all components of the given entitiy from the CES."
  [ces entity]
  (let [component-keywords (get-in ces [::entities entity])]
    (reduce #(remove-component %1 entity %2) ces component-keywords)))

(defn remove-entity
  "Removes the given entity from the CES."
  [ces entity]
  (-> ces
      (remove-all-components-of-entity entity)
      (dissoc-in [::entities entity])))

(defn last-added-entity
  "Returns the entity which was last added to the CES."
  [ces]
  (::last-id ces))

(defn make-ces
  "Takes a map and returns a CES. The key :system of the map contains
   a seq of systems to be added to the CES. It may contain duplicates.
   :entities contains a seq of components for every entity to add. The
   CES will contain all other key-value pairs of the map."
  [m]
  (let [entities (:entities m)
        ces (into {} m)]
    (-> ces
        (assoc ::next-id 0)
        (rename-keys {:systems ::systems})
        (update-in [::systems] vec)
        (dissoc :entities)
        (#(reduce (fn [ces components] (add-entity ces components))
                  %
                  entities)))))

(defn- apply-system
  "Applies the system to the CES and returns the updated CES."
  [ces system]
  {:pre [(:fn system)]}
  ((:fn system) ces))

(defn advance-ces
  "Updates the CES by applying all systems to it in their respective
   order."
  [ces]
  {:pre [(::systems ces)]}
  (reduce apply-system ces (::systems ces)))

(defmacro defcomponent
  "Defines a function with the given name which returns a component.
   It can be used exactly like 'defn'. Each of the function bodies
   must return an associative container."
  [name & r]
  (let [[doc-str-seq r] (if (string? (first r))
                          [[(first r)] (next r)]
                          [[] r])
        [attr-map-seq r] (if (map? (first r))
                           [[(first r)] (next r)]
                           [[] r])
        r (if (vector? (first r))
            (list r)
            r)
        [attr-map-seq2 bodies] (if (map? (last r))
                                 [[(last r)] (butlast r)]
                                 [[] r])
        bodies (map (fn [body] `(~@(butlast body) (assoc ~(last body) :name ~(keyword name)))) bodies)]
    `(defn ~name ~@doc-str-seq ~@attr-map-seq ~@bodies ~@attr-map-seq2)))

(defmacro defsystem
  "Defines a function with the given name which returns a system.
   Takes the arguments [name doc-string? [params*] [ces-param] body].
   The returned system is a function taking the ces and having the
   given body."
  [name & r]
  (let [[doc-str-seq r] (if (string? (first r))
                  				[[(first r)] (next r)]
                      		[[] r])
        [system-params fn-params & body] r]
    `(defn ~name ~@doc-str-seq ~system-params
       {:name ~(keyword name)
        :fn (fn ~fn-params ~@body)})))

(defmacro defcomponentsystem
  "Defines a function having the parameters [params*] with the given
   name which returns a system. Takes the arguments
   [name component-keyword doc-string? [params*] [ces-param entity-param component-param] body].
   The returned system is a function calling the given body for
   every entity having the component with the given keyword.
   The second parameters are bound to the ces the entity-id and the component respectively."
  [name component-keyword & r]
  (let [[doc-str-seq r] (if (string? (first r))
                  				[[(first r)] (next r)]
                      		[[] r])
        [system-params fn-params & body] r]
    `(defsystem ~name
       ~@doc-str-seq
       ~system-params
       [ces#]
       (let [entities# (entities-with-component ces# ~component-keyword)
             components# (map #(get-component ces# % ~component-keyword) entities#)]
         (reduce (partial apply (fn ~fn-params
                                    ~@body))
                 ces#
                 (map vector entities# components#))))))

(defmacro letc [ces-expr entity-expr bindings & body]
  (let [ces (gensym "ces")
        entity (gensym "entity")]
    `(let [~ces ~ces-expr
           ~entity ~entity-expr]
       (let ~(vec (apply concat (for [[var ks] (partition-all 2 bindings)]
                                  [var `(get-in-entity ~ces ~entity ~ks)])))
         ~@body))))
