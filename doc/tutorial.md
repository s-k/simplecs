# Ping Pong Tutorial for Simplecs

We want to simulate a very simple ping pong game.
The complete source can be found [here](../src/simplecs/examples/ping_pong.clj).
It's best to run it before reading on.

At first, we require the `simplecs.core` namespace.

``` clojure
(ns simplecs.examples.ping-pong
  (:require [simplecs.core :as sc]))
````

Simplecs is based on the assumption that each game entity is just a collection of components
which contain all of the entity data. In our case, the only entity is a ping pong ball on a
one-dimensional table. Therefore, the only attributes are its position and its velocity.

```clojure
(sc/defcomponent ball [] {})

(sc/defcomponent position [pos]
  {:pos pos})

(sc/defcomponent velocity [v]
  {:v v})
```

In the code above, we have defined three component types. The first just marks an entity as a
ball and therefore contains no data. The second holds the position of the ball. A new
`position` component is created by calling `(position 12)`. This creates a map where the key
`:pos` is mapped to 12. The `velocity` component is defined in the same way.

At this point, our entities can have associated data. However, for our game to do something
useful, we need to add some functionality. In a component-entity-system engine, all
functionality is encapsulated in so called systems.

Now, we have mentioned all three parts of a CES engine. Here is a list:
* Entities: Every entity acts as an identity for a game object. In Simplecs, entities are just unique numbers.
* Components: All game data associated with an entity is stored in a component. In Simplecs, they are implemented as Clojure maps. Every component belongs to exactly one entity.
* Systems: Every functionality of the game is implemented as a system. In Simplecs, every system is a function which takes as an argument the whole game state, which we call CES, and returns the updated game state.

Let's define our first system. Since the ping pong ball has a velocity, it has to move. This
task is handled by a system called `mover`:

``` clojure
(sc/defcomponentsystem mover :velocity
  []
  [ces entity velocity-component]
  (sc/update-entity ces
                    entity
                    [:position :pos]
                    + (:v velocity-component)))
```

Here is a walkthrough of the code: With `(defcomponentsysten mover :velocity ...)`, we define
a system called `mover` which is called exactly once for every entity which has a `velocity`
component. The `[]` means that the system is not parametrized. (You will see parametrizable
systems in a moment.) What follows is a simple function definition. The game state is bound
to `ces`, the entity ID to `entity` and the velocity component is bound to
`velocity-component`. The function body is a call to `update-entity`. It works similar to
`update-in` from `clojure.core`. The call updates the `:pos` key of the `position` component of
`entity` and adds the velocity.

```clojure
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
```

In the code snippet above, we define a system called `collider`. It is called once for every
entity which has a `ball` component and reverses the ball's velocity if it reaches the edge
of the table. It uses the `letc` macro, which works similar to `let`.
It takes the CES and an entity and then binds the given symbols to specific component entries.
`pos`, for example, is bound to the `:pos` entry of the `position` component. If `[:position]`
was used insted of `[:position :pos]`, `pos` would have been bound to the `position` component
itself. If the ball is at the edge of the table, the `velocity` component is updated. Since
the CES is a normal clojure map, we can store values in it. `(:width ces)` holds the width of
our ping pong table.

The `position-validator` system ensures that the ball stays on the table:

``` clojure
(sc/defcomponentsystem position-validator :ball
  []
  [ces entity _]
  (sc/update-entity ces
                    entity
                    [:position :pos]
                    #(max 0 (min (- (:width ces) 1) %))))
```

Until now, we have only defined systems using `defcomponentsystem`. This is useful if we want
to handle all entities with a specific component. If we want to perform a task which is
independent of a specific component, we use `defsystem`:

``` clojure
(sc/defsystem output-clearer
  [bg-symbol]
  [ces]
  (assoc ces :output (vec (repeat (:width ces) bg-symbol))))
```

The difference is that only the CES is given as an argument to the function body and that it
is executed only once instead of once for every entity with a given component. In contrast to
the previous systems, `output-clearer` is parametrizable. For example, we can instantiate the
system using `(output-clearer \.)` or `(output-clearer \_)`. As graphical output, our example
prints a string to the console. We store this string as a vector of symbols under the key
`:output` of the CES. `output-clearer` initializes our output with a vector of the size
`(:width ces)` containing only the symbol given to it when the system was initialized.

``` clojure
(sc/defcomponentsystem ball-renderer :ball
  [ball-symbol]
  [ces entity _]
  (assoc-in ces
            [:output (:pos (sc/get-component ces entity :position))]
            ball-symbol))
```

The system `ball-renderer` simply replaces the symbol in the output vector at the position of
the ball with the symbol given when the system is instantiated. `get-component` is used to
obtain the `position` component of the entity.

``` clojure
(sc/defcomponentsystem ping-pong-renderer :ball
  []
  [ces entity _]
  (sc/letc ces entity
           [pos [:position :pos]]
    (cond (= pos 0) (assoc ces :output [\P \I \N \G \!])
          (= pos (- (:width ces) 1)) (assoc ces :output [\P \O \N \G \!])
          :default ces)))
```

Because our small game has no sound output, we have to visualize the ping and pong sounds.
`ping-pong-renderer` does this job. When the ball is at the left-most position, it replaces
the output vector with one that says 'PING!' and if the ball is at the right-most position,
the output gets changed to 'PONG!'.

``` clojure
(sc/defsystem output-converter
  []
  [ces]
  (update-in ces [:output] #(apply str %)))
```

Our last component simply converts the output from a vector to a string.

Now it's time to put everything together:

``` clojure
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
```

We define `ces` as an atom containing our CES, which is created by the function `make-ces`.
The function takes a map containing the entities, the systems and any entries which shall be
included in the CES. The `:entities` entry is a list of entites, which in turn are just a
list of components. In our case, we have only one entity, which is a `(ball)`, has the
`(position -1)` and the `(velocity 1)`. Notice, that each entity can only have one component
of each type. The `:systems` entry is just a list of systems in the order in which they shall
be executed for every step.

``` clojure
(defn update []
  (swap! ces sc/advance-ces)
  (println (:output @ces)))
```

The `update` function advances the ces and prints its output to the console. The function
`advance-ces` takes the CES and returns the updated CES to which all its systems have been
applied.

Now, we have everything we need. Let's try it out:

``` clojure
(dotimes [i 20] (update))
```
