(ns meo.electron.renderer.ui.react-list
  (:require [meo.electron.renderer.ui.entry.entry :as e]
            [taoensso.timbre :refer [info error debug]]
            [reagent.ratom :refer-macros [reaction]]
            [re-frame.core :refer [subscribe]]
            [react-list :as rl]
            [reagent.core :as r]))

(def react-list (r/adapt-react-class rl))

(defn entry-wrapper [idx local-cfg _put-fn]
  (let [tab-group (:tab-group local-cfg)
        gql-res (subscribe [:gql-res])
        entry (reaction (get-in @gql-res [:tabs-query :data tab-group idx]))]
    (fn entry-wrapper-render [_idx local-cfg put-fn]
      ^{:key (str (:timestamp @entry) (:vclock @entry))}
      [e/entry-with-comments @entry put-fn local-cfg])))

(defn item [local-cfg put-fn]
  (fn [idx]
    (r/as-element
      [:div {:key idx}
       [entry-wrapper idx local-cfg put-fn]])))

(defn journal-view
  "Renders journal div, one entry per item, with map if geo data exists in the
   entry."
  [local-cfg _put-fn]
  (let [gql-res (subscribe [:gql-res])
        tab-group (:tab-group local-cfg)
        entries-list (reaction (get-in @gql-res [:tabs-query :data tab-group]))]
    (fn journal-view-render [local-cfg put-fn]
      (let [query-id (:query-id local-cfg)
            tg (:tab-group local-cfg)
            on-scroll (fn [ev]
                        (let [elem (-> ev .-nativeEvent .-srcElement)
                              sh (.-scrollHeight elem)
                              st (.-scrollTop elem)]
                          (when (< (- sh st) 1000)
                            (put-fn [:show/more {:query-id query-id}]))))
            on-mouse-enter #(put-fn [:search/cmd {:t         :active-tab
                                                  :tab-group tg}])]
        ^{:key (str query-id)}
        [:div.journal {:on-mouse-enter on-mouse-enter}
         [:div.journal-entries {:on-scroll on-scroll}
          [react-list {:length       (count @entries-list)
                       :itemRenderer (item local-cfg put-fn)}]]]))))