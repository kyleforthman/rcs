(ns com.rcs.app
  (:require [com.biffweb :as biff :refer [q]]
            [com.rcs.middleware :as mid]
            [com.rcs.ui :as ui]
            [com.rcs.settings :as settings]
            [rum.core :as rum]
            [xtdb.api :as xt]
            [ring.adapter.jetty9 :as jetty]
            [cheshire.core :as cheshire]))

(defn name-form [name]
  (biff/form
   {:action "/app/set-name"}
   [:label.block {:for "name"} "Name: "
    [:span.font-mono name]]
   [:.h-1]
   [:.flex
    [:input.w-full#foo {:type "text" :name "name" :value name}]
    [:.w-3]
    [:button.btn {:type "submit"} "Update"]]))

(defn set-name [{:keys [session params] :as ctx}]
  (biff/submit-tx ctx
    [{:db/op :update
      :db/doc-type :user
      :xt/id (:uid session)
      :user/name (:name params)}])
  {:status 303
   :headers {"location" "/app"}})

(defn note-form [content]
  (biff/form
   {:action "/app/write-note"}
   [:label.block {:for "note"} "Note: "
    [:span.font-mono content]]
   [:.h-1]
   [:.flex
    [:input.w-full#note {:type "text" :name "note" :value content}]
    [:.w-3]
    [:button.btn {:type "submit"} "Update"]]))

(defn set-note [{:keys [session params] :as ctx}]
  (let [note-id (random-uuid)]
    (biff/submit-tx ctx
                 [{:db/doc-type :note
                   :xt/id note-id
                   :note/owner (:uid session)
                   :note/text (:note params)
                   :note/timestamp (biff/now)}]))
  {:status 303
   :headers {"location" "/app"}})

(defn bar-form [{:keys [value]}]
  (biff/form
   {:hx-post "/app/set-bar"
    :hx-swap "outerHTML"}
   [:label.block {:for "bar"} "Bar: "
    [:span.font-mono (pr-str value)]]
   [:.h-1]
   [:.flex
    [:input.w-full#bar {:type "text" :name "bar" :value value}]
    [:.w-3]
    [:button.btn {:type "submit"} "Update"]]
   [:.h-1]
   [:.text-sm.text-gray-600
    "This demonstrates updating a value with HTMX."]))

(defn set-bar [{:keys [session params] :as ctx}]
  (biff/submit-tx ctx
    [{:db/op :update
      :db/doc-type :user
      :xt/id (:uid session)
      :user/bar (:bar params)}])
  (biff/render (bar-form {:value (:bar params)})))

(defn message [{:msg/keys [text sent-at]}]
  [:.mt-3 {:_ "init send newMessage to #message-header"}
   [:.text-gray-600 (biff/format-date sent-at "dd MMM yyyy HH:mm:ss")]
   [:div text]])

(defn notify-clients [{:keys [com.rcs/chat-clients]} tx]
  (doseq [[op & args] (::xt/tx-ops tx)
          :when (= op ::xt/put)
          :let [[doc] args]
          :when (contains? doc :msg/text)
          :let [html (rum/render-static-markup
                      [:div#messages {:hx-swap-oob "afterbegin"}
                       (message doc)])]
          ws @chat-clients]
    (jetty/send! ws html)))

(defn send-message [{:keys [session] :as ctx} {:keys [text]}]
  (let [{:keys [text]} (cheshire/parse-string text true)]
    (biff/submit-tx ctx
      [{:db/doc-type :msg
        :msg/user (:uid session)
        :msg/text text
        :msg/sent-at :db/now}])))

(defn chat [{:keys [biff/db]}]
  (let [messages (q db
                    '{:find (pull msg [*])
                      :in [t0]
                      :where [[msg :msg/sent-at t]
                              [(<= t0 t)]]}
                    (biff/add-seconds (java.util.Date.) (* -60 10)))]
    [:div {:hx-ext "ws" :ws-connect "/app/chat"}
     [:form.mb-0 {:ws-send true
                  :_ "on submit set value of #message to ''"}
      [:label.block {:for "message"} "Write a message"]
      [:.h-1]
      [:textarea.w-full#message {:name "text"}]
      [:.h-1]
      [:.text-sm.text-gray-600
       "Sign in with an incognito window to have a conversation with yourself."]
      [:.h-2]
      [:div [:button.btn {:type "submit"} "Send message"]]]
     [:.h-6]
     [:div#message-header
      {:_ "on newMessage put 'Messages sent in the past 10 minutes:' into me"}
      (if (empty? messages)
        "No messages yet."
        "Messages sent in the past 10 minutes:")]
     [:div#messages
      (map message (sort-by :msg/sent-at #(compare %2 %1) messages))]]))

(defn note-card [note]
  (let [{:keys [xt/id note/owner note/title note/text note/timestamp]} note]
    [:div {:class "w-1/3 md:w-1/4 lg:w-1/5 px-2 mb-4"}
      [:div {:class "flex flex-col bg-white rounded-md shadow p-4 mb-2 w-full h-full"
             :on-mouse-enter (str "document.getElementById('note-" id "-buttons').classList.remove('hidden');"
                                  "document.getElementById('content-" id "').classList.add('hidden')")
             :on-mouse-leave (str "document.getElementById('note-" id "-buttons').classList.add('hidden');"
                                  "document.getElementById('content-" id "').classList.remove('hidden')")}
        [:div {:id (str "content-" id)} text]
        [:div {:id (str "note-" id "-buttons") :class '[flex flex-col hidden justify-center items-center]}
          [:a.hover:underline {:href (str "/note/" id)} "View"]
          [:a.hover:underline {:href (str "/note/" id "/edit")} "Edit"]
          [:a.text-red-400.hover:underline {:href (str "/note/" id "/delete")} "Delete"]]]]))

(defn notes-list [notes]
  (if (seq notes)
    [:div
     [:.h-3]
     [:div "Notes: "
       [:div.flex.flex-wrap
         (for [note notes]
           (note-card note))]]]
    [:div "You have no notes."]))



(defn app [{:keys [session biff/db] :as ctx}]
  (let [{:user/keys [email name content bar]} (xt/entity db (:uid session))]
    (ui/page
     {}
     [:div "Signed in as " email ". "
      (biff/form
       {:action "/auth/signout"
        :class "inline"}
       [:button.text-blue-500.hover:text-blue-800 {:type "submit"}
        "Sign out"])
      "."]
     [:.h-6]
     (name-form name)
     [:.h-6]
     (note-form content)
     [:.h-6]
     (let [notes (biff/lookup-all db :note/owner (:uid session))]
       (notes-list notes))
     [:.h-6]
     (bar-form {:value bar})
     [:.h-6]
     (chat ctx))))

(defn ws-handler [{:keys [com.rcs/chat-clients] :as ctx}]
  {:status 101
   :headers {"upgrade" "websocket"
             "connection" "upgrade"}
   :ws {:on-connect (fn [ws]
                      (swap! chat-clients conj ws))
        :on-text (fn [ws text-message]
                   (send-message ctx {:ws ws :text text-message}))
        :on-close (fn [ws status-code reason]
                    (swap! chat-clients disj ws))}})

(def about-page
  (ui/page
   {:base/title (str "About " settings/app-name)}
   [:p "This app was made with "
    [:a.link {:href "https://biffweb.com"} "Biff"] "."]))

(defn echo [{:keys [params]}]
  {:status 200
   :headers {"content-type" "application/json"}
   :body params})

(def plugin
  {:static {"/about/" about-page}
   :routes ["/app" {:middleware [mid/wrap-signed-in]}
            ["" {:get app}]
            ["/set-name" {:post set-name}]
            ["/write-note" {:post set-note}]
            ["/set-bar" {:post set-bar}]
            ["/chat" {:get ws-handler}]]
   :api-routes [["/api/echo" {:post echo}]]
   :on-tx notify-clients})
