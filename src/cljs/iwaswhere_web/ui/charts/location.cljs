(ns iwaswhere-web.ui.charts.location
  (:require [reagent.core :as rc]
            [re-frame.core :refer [subscribe]]
            [iwaswhere-web.ui.charts.common :as cc]
            [iwaswhere-web.helpers :as h]
            [cljs.pprint :as pp]))

(defn loc-table [expanded? per-location label]
  (let [emoji-flags (aget js/window "deps" "emojiFlags")]
    (when (and expanded? (seq per-location))
      [:table
       [:tbody
        [:tr
         [:th "Rank"]
         [:th "Flag"]
         [:th label]
         [:th "Days"]]
        (for [[i [loc cnt]] per-location]
          (let [country (js->clj (.countryCode emoji-flags (:country loc)))
                flag (get country "emoji")]
            ^{:key loc}
            [:tr
             [:td.rank (inc i) "."]
             [:td.flag flag]
             [:td.country (:name loc)]
             [:td.cnt cnt]]))]])))

(defn location-chart [chart-h put-fn]
  (let [local (rc/atom {:last-fetched 0
                        :expand       false})
        stats (subscribe [:stats])
        emoji-flags (aget js/window "deps" "emojiFlags")
        last-update (subscribe [:last-update])]
    (fn [chart-h put-fn]
      (let [loc-stats (:locations @stats)
            expanded? (:expanded @local)
            per-entity (fn [k]
                         (->> (:locations @stats)
                              k
                              (sort-by second)
                              (filter #(identity (first %)))
                              reverse
                              (map-indexed (fn [idx v] [idx v]))))
            per-country (per-entity :days-per-country)
            per-location (per-entity :days-per-location)
            per-admin-1 (per-entity :days-per-admin-1)
            per-admin-2 (per-entity :days-per-admin-2)
            per-admin-3 (per-entity :days-per-admin-3)
            per-admin-4 (per-entity :days-per-admin-4)]
        [:div.location-stats
         {:class (when expanded? "expanded")}
         [:div.content.white
          [:div.expand {:on-click #(swap! local update-in [:expanded] not)}
           [:span.fa {:class (if expanded? "fa-compress" "fa-expand")}]]
          [:table
           [:tbody
            (when expanded?
              [:tr
               [:th "Rank"]
               [:th "Flag"]
               [:th "Country"]
               [:th "Days"]])
            (for [[i [cc cnt]] (if expanded? per-country (take 10 per-country))]
              (let [country (js->clj (.countryCode emoji-flags cc))
                    flag (get country "emoji")
                    cname (get country "name")]
                ^{:key cc}
                [:tr
                 [:td.rank (inc i) "."]
                 [:td.flag flag]
                 [:td.country cname]
                 [:td.cnt cnt]]))]]
          [loc-table expanded? per-location "Location"]
          [loc-table expanded? per-admin-1 "Admin1"]
          [loc-table expanded? per-admin-2 "Admin2"]
          [loc-table expanded? per-admin-3 "Admin3"]
          [loc-table expanded? per-admin-4 "Admin4"]
          (when expanded?
            [:div#plotly])]]))))