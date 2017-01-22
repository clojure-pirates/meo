(ns iwaswhere-web.ui.entry.story
  (:require [iwaswhere-web.helpers :as h]
            [iwaswhere-web.ui.entry.capture :as c]
            [re-frame.core :refer [subscribe]]
            [reagent.ratom :refer-macros [reaction]]))

(defn editable-field
  [on-input-fn on-keydown-fn text]
  [:div.story-edit-field
   {:content-editable true
    :on-input         on-input-fn
    :on-key-down      on-keydown-fn}
   text])

(defn story-name
  "Renders editable field for story name when the entry is of type :story.
   Updates local entry on input, and saves the entry when CMD-S is pressed."
  [entry put-fn]
  (when (= (:entry-type entry) :story)
    (let [on-input-fn (fn [ev]
                        (let [text (aget ev "target" "innerText")
                              updated (assoc-in entry [:story-name] text)]
                          (put-fn [:entry/update-local updated])))
          on-keydown-fn (fn [ev]
                          (let [text (aget ev "target" "innerText")
                                updated (assoc-in entry [:story-name] text)
                                key-code (.. ev -keyCode)
                                meta-key (.. ev -metaKey)]
                            (when (and meta-key (= key-code 83)) ; CMD-s pressed
                              (put-fn [:entry/update updated])
                              (.preventDefault ev))))]
      [:div.story
       [:label "Story:"]
       [editable-field on-input-fn on-keydown-fn (:story-name entry)]])))

(defn story-select
  "In edit mode, allow editing of activities, otherwise show a summary."
  [entry put-fn edit-mode?]
  (let [options (subscribe [:options])
        stories (reaction (:stories @options))
        sorted-stories (reaction (:sorted-stories @options))
        ts (:timestamp entry)
        new-entries (subscribe [:new-entries])
        select-handler
        (fn [ev]
          (let [selected (js/parseInt (-> ev .-nativeEvent .-target .-value))
                custom-path (get-in @stories [selected :custom-path])
                updated (-> (get-in @new-entries [ts])
                            (assoc-in [:linked-story] selected)
                            (assoc-in [:custom-path] custom-path))]
            (put-fn [:entry/update-local updated])))]
    (fn story-select-render [entry put-fn edit-mode?]
      (let [linked-story (:linked-story entry)]
        (if edit-mode?
          (when-not (or (= (:entry-type entry) :story) (:comment-for entry))
            [:div.story
             [:label "Story:"]
             [:select {:value     (or linked-story "")
                       :on-change select-handler}
              [:option {:value ""} "no story selected"]
              (for [[id story] @sorted-stories]
                (let [story-name (:story-name story)]
                  ^{:key (str ts story-name)}
                  [:option {:value id} story-name]))]])
          (when linked-story
            [:div.story (:story-name (get @stories linked-story))]))))))