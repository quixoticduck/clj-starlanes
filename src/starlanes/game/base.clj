(ns starlanes.game.base
  (:require [clojure.string :as string]
            [starlanes.const :as const]
            [starlanes.finance :as finance]
            [starlanes.game.map :as game-map]
            [starlanes.game.movement :as game-move]
            [starlanes.game.command :as game-command]
            [starlanes.layout :as layout]
            [starlanes.player :as player]
            [starlanes.util :as util]))


(declare do-player-turn)

(defn game-data-factory []
  {:star-map (sorted-map),
   :total-moves 0,
   :players [],
   :player-order [],
   :move 0,
   :companies [],
   :share-value {},
   :rand (util/random const/seed)})

(defn create-star-map-for-game [game-data]
  (let [star-map (game-map/create-star-map game-data)]
    (conj game-data {:star-map star-map})))

(defn set-new-players
  ([]
    (set-new-players (game-data-factory)))
  ([game-data]
    (conj game-data {:players (doall (player/get-new-players))})))

(defn get-player-move []
  (util/input (str \newline "What is your move? ")))

(defn tally-scores [game-data]
  (util/display (str \newline
                     "Tallying scores ..." \newline))
  ; XXX get top-score
  ; XXX determine tie-breaking, if necessary
  ; XXX display "scoreboard"
  )

(defn parse-command
  "Some of the command functions will return new game-data (e.g., 'load-game');
  all the rest should return 'nil'."
  [game-data command]
  (cond
    (util/in? ["map" "m"] command) nil
    (util/in? ["order" "o"] command)
      (game-command/display-player-order game-data)
    (= command "score")
      (game-command/display-score game-data)
    (util/in? ["help" "h"] command)
      (game-command/display-help)
    (= command "commands")
      (game-command/display-commands)
    (= command "save")
      (game-command/save-game game-data)
    (= command "load")
      (game-command/load-game)
    (util/in? ["stock" "s"] command)
      (finance/display-stock game-data)
    (util/in? ["quit" "q" "exit" "x"] command)
      (game-command/quit-game tally-scores game-data)))

(defn process-command
  "For command functions that return 'nil', simply run 'do-player-turn' again
  with the same moves. If a command function does not return 'nil', it's
  likely returning new game-data, and moves should not be included, as they
  will be recalculated. This is the case when loading game data from a file."
  [game-data available-moves command]
  (let [post-parse-game-data (parse-command game-data command)]
    (cond
      (nil? post-parse-game-data)
        (do-player-turn
          game-data
          available-moves)
      :else
        (do-player-turn
          post-parse-game-data)
      )))

(defn process-move [game-data move]
  (let [item-char (game-move/get-character-for-move game-data move)]
    (do-player-turn
      (game-move/inc-move
        (game-map/update-coords move item-char game-data)))))

(defn display-map-and-moves [game-data available-moves]
  (game-move/check-remaining-moves available-moves game-data)
  (layout/draw-grid game-data)
  (game-command/display-moves available-moves game-data))

(defn do-bad-input [game-data available-moves input]
  (util/display (str \newline "Whoops! Your input of '" input
                     "' was not understood. Please try again."
                     \newline \newline))
  (util/input const/continue-prompt)
  (do-player-turn
    game-data
    available-moves))

(defn validate-move [game-data available-moves move]
  (cond
    (util/in? available-moves move)
      (process-move game-data move)
    (util/in? const/commands move)
      (process-command game-data available-moves move)
    :else (do-bad-input game-data available-moves move)))

(defn do-player-turn
  ([game-data]
    (do-player-turn
      game-data
      (game-move/get-friendly-moves game-data)))
  ([game-data available-moves]
    (display-map-and-moves game-data available-moves)
    (validate-move
      game-data
      available-moves
      (string/lower-case
        (get-player-move)))))