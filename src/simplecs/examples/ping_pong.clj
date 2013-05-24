(ns simplecs.examples.ping-pong
  (:require [simplecs.core :as sc]))

(sc/defcomponent ball [] {})

(sc/defcomponent position [pos]
  {:pos pos})

(sc/defcomponent velocity [v]
  {:v v})

(sc/defcomponentsystem mover :velocity
  []
  [ces entity velocity-component]
  (sc/update-entity ces
                    entity
                    [:position :pos]
                    + (:v velocity-component)))

(sc/defcomponentsystem collider :ball
  []
  [ces entity _]
  (sc/letc ces entity
           [pos [:position :pos]
            v [:velocity :v]]
    (if (or (and (< pos 1)
                 (< v 0))
            (and (>= pos (- (:width ces) 1))
                 (> v 0)))
      (sc/update-entity ces entity [:velocity :v] -)
      ces)))

(sc/defcomponentsystem position-validator :ball
  []
  [ces entity _]
  (sc/update-entity ces
                    entity
                    [:position :pos]
                    #(max 0 (min (- (:width ces) 1) %))))

(sc/defsystem output-clearer
  [bg-symbol]
  [ces]
  (assoc ces :output (vec (repeat (:width ces) bg-symbol))))

(sc/defcomponentsystem ball-renderer :ball
  [ball-symbol]
  [ces entity _]
  (assoc-in ces
            [:output (:pos (sc/get-component ces entity :position))]
            ball-symbol))

(sc/defcomponentsystem ping-pong-renderer :ball
  []
  [ces entity _]
  (sc/letc ces entity
           [pos [:position :pos]]
    (cond (= pos 0) (assoc ces :output [\P \I \N \G \!])
          (= pos (- (:width ces) 1)) (assoc ces :output [\P \O \N \G \!])
          :default ces)))

(sc/defsystem output-converter
  []
  [ces]
  (update-in ces [:output] #(apply str %)))

(def ces
  (atom (sc/make-ces {:entities [[(ball)
                                  (position -1)
                                  (velocity 1)]]
                      :systems [(mover)
                                (collider)
                                (position-validator)
                                (output-clearer \.)
                                (ball-renderer \o)
                                (ping-pong-renderer)
                                (output-converter)]
                      :width 5})))

(defn update []
  (swap! ces sc/advance-ces)
  (println (:output @ces)))

(dotimes [i 20] (update))
