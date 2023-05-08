(ns com.rcs.schema)

(def schema
  {:user/id :uuid
   :user [:map {:closed true}
          [:xt/id                     :user/id]
          [:user/email                :string]
          [:user/joined-at            inst?]
          [:user/name {:optional true} :string]
          [:user/bar {:optional true} :string]]

   :work/id :uuid
   :work [:map {:closed true}
          [:xt/id :work/id]
          [:work/owner :user/id]
          [:work/title :string]
          [:work/pitch {:optional true} :string]
          [:work/chapters {:optional true} [:vector :chapter/id]]
          [:work/notes {:optional true} [:vector :note/id]]]

   :chapter/id :uuid
   :chapter [:map {:closed true}
             [:xt/id :chapter/id]
             [:chapter/title :string]
             [:chapter/content {:optional true} :string]
             [:chapter/notes {:optional true} [:vector :note/id]]]

   :note/id :uuid
   :note [:map {:closed true}
          [:xt/id :note/id]
          [:note/owner :user/id]
          [:note/text :string]
          [:note/timestamp inst?]]

   :msg/id :uuid
   :msg [:map {:closed true}
         [:xt/id       :msg/id]
         [:msg/user    :user/id]
         [:msg/text    :string]
         [:msg/sent-at inst?]]})

(def plugin
  {:schema schema})
