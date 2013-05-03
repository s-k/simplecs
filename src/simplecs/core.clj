(ns simplecs.core
  (:require [clojure.set :refer (rename-keys)]))

(defn- dissoc-in
  "Dissociates an entry from a nested associative structure returning a new
  nested structure. keys is a sequence of keys. Any empty maps that result
  will not be present in the new structure."
  [m [k & ks :as keys]]
  (if ks
    (if-let [nextmap (get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (if (seq newmap)
          (assoc m k newmap)
          (dissoc m k)))
      m)
    (dissoc m k)))

(defn entities-with-component
  "Returns a seq with all entity-ids of 'ces' which
   have the component with the 'component-keyword'."
  [ces component-keyword]
  (keys (get-in ces [::components component-keyword])))

(defn has-component?
  "Returns true if the entity with 'entity-id'
   has the component with 'component-keyword'."
  [ces entity-id component-keyword]
  (get-in ces [::entities entity-id component-keyword]))

(defn get-component
  "Returns the component with 'component-keyword'
   of the entity with 'entity-id'."
  [ces entity-id component-keyword]
  (get-in ces [::components component-keyword entity-id]))

(defn update-component
  "Returns an updated CES where the component with 'component-keyword'
   of the entity with 'entity-id' is updated using function 'f'."
  [ces entity-id component-keyword f & r]
  {:pre [(has-component? ces entity-id component-keyword)]
   :post [(:name (get-in % [::components component-keyword entity-id]))]}
  (apply update-in ces [::components component-keyword entity-id] f r))

(defn add-component
  "Returns an updated CES where for the entity with 'entity-id' the
   new 'component' is added."
  [ces entity-id component]
  (-> ces
      (assoc-in [::components (:name component) entity-id] component)
      (update-in [::entities entity-id] #(conj % (:name component)))))

(defn remove-component
  "Returns an updated CES where the component with
   'component-keyword' is removed from the entity with 'entity-id'."
  [ces entity-id component-keyword]
  (-> ces
      (dissoc-in [::components component-keyword entity-id])
      (update-in [::entities entity-id] #(disj % component-keyword))))

(defn component-keywords-for-entity
  "Returns a set of the keywords for all
   components of the entity with 'entity-id'."
  [ces entity-id]
  (get-in ces [::entities entity-id]))

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
  "Removes all components of the entitiy with the given ID from the CES."
  [ces entity-id]
  (let [component-keywords (get-in ces [::entities entity-id])]
    (reduce #(remove-component %1 entity-id %2) ces component-keywords)))

(defn remove-entity
  "Removes the entity with the given ID from the CES."
  [ces entity-id]
  (-> ces
      (remove-all-components-of-entity entity-id)
      (dissoc-in [::entities entity-id])))

(defn last-added-entity-id
  "Returns the id of the entity which was last added to the CES."
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
   Takes the arguments [name doc-string? [params*] keys-and-vals*].
   The returned component contains the given keys and values."
  [name & r]
  (let [[doc-str-seq r] (if (string? (first r))
                  				[[(first r)] (next r)]
                      		[[] r])
        [params & entries] r]
    `(defn ~name ~@doc-str-seq ~params
       ~(into {:name (keyword name)}
              (map vec (partition 2 entries))))))

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
