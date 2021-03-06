(ns meo.jvm.imports
  (:require [meo.jvm.imports.spotify :as iss]
            [meo.jvm.imports.flight :as fl]
            [meo.jvm.imports.git :as g]
            [meo.jvm.imports.media :as im]))

(defn cmp-map [cmp-id]
  {:cmp-id      cmp-id
   :opts        {:in-chan  [:buffer 100]
                 :out-chan [:buffer 100]}
   :handler-map {:import/photos     im/import-photos
                 :import/gen-thumbs im/gen-thumbs
                 :import/movie      im/import-movie
                 :import/spotify    iss/import-spotify
                 :import/git        g/import-from-git
                 :spotify/play      iss/spotify-play
                 :spotify/pause     iss/spotify-pause
                 :photos/gen-cache  im/gen-cache
                 :import/flight     fl/import-flight}})
