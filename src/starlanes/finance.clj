(ns starlanes.finance
  (:require [clojure.set :as set]
            [starlanes.const :as const]
            [starlanes.game.map :as game-map]
            [starlanes.player :as player]
            [starlanes.util :as util]))


(defn get-new-stock-exchange
  ([companies-letters]
    (util/get-map-of-maps (map keyword companies-letters)))
  ([companies-letters players-names]
    (let [players-holdings (util/get-map-of-maps players-names)]
      (util/get-map-of-maps (map keyword companies-letters) players-holdings))))

(defn get-stock-exchange [game-data]
  (game-data :stock-exchange))

(defn get-company-holdings
  ""
  [company-letter game-data]
  ((get-stock-exchange game-data) (keyword company-letter)))

(defn get-company-shares
  ""
  [company-letter game-data]
  (reduce +
          (map
            (fn [x] (x :shares))
            (vals
              (get-company-holdings company-letter game-data)))))

(defn get-player-shares
  ""
  [company-letter player-name game-data]
  (let [company-holdings (get-company-holdings company-letter game-data)]
    (if company-holdings
      (let [player-holdings (company-holdings player-name)]
        (if player-holdings
          (let [player-shares (player-holdings :shares)]
            (if player-shares
              player-shares
              0))
          0))
      0)))

(defn get-players-shares
  ""
  ([game-data]
    (get-players-shares (util/get-companies-letters) game-data))
  ([companies-letters game-data]
    (into {}
          (map
            (fn [player-name]
              [player-name (into {}
                       (map
                         (fn [company-letter]
                           [(keyword company-letter)
                            (get-player-shares
                              company-letter player-name game-data)])
                         companies-letters))])
            (player/get-players-names game-data)))))

(defn company-factory []
  {:name ""
   :share-mod 0.0
   :units 0})

(defn get-new-company
  ([]
    (company-factory))
  ([name units share-mod]
    (assoc
      (company-factory)
      :name name
      :units units
      :share-mod share-mod)))

(defn get-player-stock
  ""
  [player-name game-data]
  )

(defn display-stock [game-data]
  (util/display
    (str \newline "Here's your current stock: " \newline \newline))
  (util/input const/continue-prompt)
  nil)

(defn compute-stock-value [{stock :stock value :value}]
  (* stock value))

(defn compute-stocks-value [stocks-data]
  (reduce + (map compute-stock-value stocks-data)))

(defn compute-value
  "The assets parameter is a map which has the following structure:
    {:cash <float> :stock <integer> :value <float>}
  where :value is stock price of the associated stock."
  ([assets]
    (apply compute-value assets))
  ([cash stocks-data]
    (+ cash (compute-stocks-value stocks-data))))

(defn display-value [value]
  )

(defn get-next-company [game-data]
  (let [current-count (count (game-data :companies))
        next-index (inc current-count)]))

(defn add-company [units share-mod game-data]
  (let [available (game-data :companies-queue)
        company-name (first available)
        game-data (conj game-data {:companies-queue (rest available)})
        company (get-new-company company-name units share-mod)
        companies (concat (game-data :companies) [company])]
    [company-name (conj game-data {:companies companies})]))

(defn filter-company
  "Reomve company data whose names match the passed name."
  [company-name companies]
  (filter
    (fn [x]
      (not= (x :name) company-name))
    companies))

(defn remove-company [company-name game-data]
  (let [companies-queue (sort
                          (concat
                            (game-data :companies-queue) [company-name]))
        companies (filter-company company-name (game-data :companies))]
    (conj game-data {:companies companies :companies-queue companies-queue})))

(defn announce-new-company [company-name]
  (util/beep)
  (util/display
    (str
      "A new shipping company has been formed!" \newline
      "It's name is '" company-name "'." \newline)))

(defn announce-player-bonus [current-player company-name units mod]
  (util/display
    (str
      \newline
      (current-player :name) ", you have been awarded "
      const/founding-shares " share(s) in " \newline
      company-name ", currently valued at " \newline
      (* units mod) " "
      const/currency-name "s each." \newline \newline))
  (util/input const/continue-prompt))

(defn make-announcements [current-player company-name units mod]
  (announce-new-company company-name)
  (announce-player-bonus current-player company-name units mod))

(defn update-player-stock [current-player units game-data]
  game-data
  )

(defn create-company
  "Creating a company should only ever happen if all attempts have been made
  to identify possibly merges, first. As such, there should never be another
  company in the immediate neighborhood (adjoining spaces) of a new company;
  there should only be stars and/or outposts."
  [keyword-coord current-player share-modifier game-data]
  (let [outpost-coords (map first (game-map/get-neighbor-outposts
                                    keyword-coord game-data))
        units (count outpost-coords)
        [company-name game-data] (add-company units share-modifier game-data)
        item-char (str (first company-name))]
    (make-announcements
      current-player company-name (inc units) share-modifier)
    (update-player-stock
      current-player
      units
      (game-map/multi-update-coords
        (concat outpost-coords [keyword-coord])
        item-char
        game-data))))

(defn create-star-company
  "Update the game data with a new company created from adjacent outposts."
  [keyword-coord current-player game-data]
  (create-company
    keyword-coord current-player const/share-modifier-star game-data))

(defn create-outpost-company
  "Update the game data with a new company created from adjacent outposts."
  [keyword-coord current-player game-data]
  (create-company
    keyword-coord current-player const/share-modifier-base game-data))

(defn get-companies-base-counts
  "For each company, count the number of pieces (bases) they have on the board."
  [game-data]
  (let [company-letters (map second (game-map/get-companies-data game-data))]
    (util/count-occurances company-letters)))

(defn get-company-base-count
  ""
  [company-letter game-data]
  (let [star-count ((get-companies-base-counts game-data) company-letter)]
    (if (nil? star-count)
      0
      star-count)))

(defn get-companies-star-counts
  "Get all companies that are next to stars and the number of stars they are
  next to."
  [game-data]
  (let [stars (game-map/get-star-coords game-data)
        star-neighbors (map
                         #(game-map/get-neighbor-companies % game-data)
                         stars)]
    (util/count-occurances
      (take-nth 2
        (rest
          (flatten
            (remove empty? star-neighbors)))))))

(defn get-company-star-count
  "For the commpany letter passed, count the number of company bases that are
  adjacent to a star."
  [company-letter game-data]
  (let [star-count ((get-companies-star-counts game-data) company-letter)]
    (if (nil? star-count)
      0
      star-count)))

(defn get-share-value
  ""
  [company-letter game-data]
  (let [star-count (get-company-star-count company-letter game-data)
        base-count (get-company-base-count company-letter game-data)]
    (+
      (* const/share-modifier-star star-count)
      (* const/share-modifier-base base-count))))

(defn get-company-value
  "Things that affect company value:
    * total number of shares held by all players
    * value of shares

  Value of shares is affected by:
    * number of company pieces on the board
    * number of company pieces on the board adjacent to stars"
  [company-letter game-data]
  (let [share-value (get-share-value company-letter game-data)
        total-company-shares (get-company-shares company-letter game-data)]
    (* total-company-shares share-value)))

(defn get-companies-values
  "For each company in the game, get its value and return a hash map with this
  data."
  ([game-data]
   (get-companies-values (util/get-companies-letters) game-data))
  ([companies-letters game-data]
    (into
      {}
      (map
        (fn [x] {(keyword x) (get-company-value x game-data)})
        companies-letters))))

(defn get-filtered-companies
  ""
  [companies-letters game-data]
  (filter
    (fn [x] (util/in? companies-letters (second x)))
    (game-map/get-companies-data game-data)))

(defn get-greatest-company
  "Get all company values, and identify those with the greatest value. In the
  event of a tie, randomly select from the top-valued companies."
  [companies-letters game-data]
  (let [data (get-companies-values companies-letters game-data)
        sorted (reverse
                 (sort
                   (map
                     (fn [x] [(val x) (key x)])
                     data)))]
    (first
      (rand-nth
        (remove
          empty?
          (map
            (fn [[val key]]
              (if
                (= val (first (first sorted))) [key val]))
            sorted))))))

(defn merge-companies
  ""
  [keyword-coord current-player companies game-data]
  (let [distinct-companies (distinct (map second companies))
        coord-data (get-filtered-companies distinct-companies game-data)
        companies-values (get-companies-values distinct-companies game-data)]
    (util/display (str \newline "Merging companies ..." \newline))
    (util/input const/continue-prompt)
    ; XXX get a list of company letters elligible for merging
    ; XXX get the top-valued company(s)
    ; XXX for the loser, replace all the entries in the star map with the letter
    ; from the winner
    ; XXX recalculate value of winning company, with map updated
    ; XXX if the stock is over the threshold, perform a split
    game-data))

(defn expand-company
  ""
  [keyword-coord current-player company-item-data game-data]
  (let [company-letter (second company-item-data)
        outpost-coords (map first (game-map/get-neighbor-outposts
                                    keyword-coord game-data))]
    ; XXX this is an insufficient final solution; see the following issue for
    ; more details:
    ;   https://github.com/oubiwann/star-traders/issues/7
    ; we're going to want to create a function that takes a list of neighbor
    ; outposts, converts them to the company, and then recursing on all those
    ; outposts' neighbors that are outposts, performing the same action
    (game-map/multi-update-coords
      (concat outpost-coords [keyword-coord])
      company-letter
      game-data)))



