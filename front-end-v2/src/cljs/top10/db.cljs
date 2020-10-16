(ns top10.db)

(def default-db
  {:active-panel :home-panel
   :session {:access-token nil
             :checking-status true
             :csrf-token nil
             :logged-in false}})
