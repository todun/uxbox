;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; This Source Code Form is "Incompatible With Secondary Licenses", as
;; defined by the Mozilla Public License, v. 2.0.
;;
;; Copyright (c) 2020 UXBOX Labs SL

(ns app.main.ui.dashboard.sidebar
  (:require
   [app.common.data :as d]
   [app.common.spec :as us]
   [app.config :as cfg]
   [app.main.constants :as c]
   [app.main.data.auth :as da]
   [app.main.data.dashboard :as dd]
   [app.main.data.messages :as dm]
   [app.main.refs :as refs]
   [app.main.repo :as rp]
   [app.main.store :as st]
   [app.main.ui.components.dropdown :refer [dropdown]]
   [app.main.ui.components.forms :refer [input submit-button form]]
   [app.main.ui.dashboard.team-form]
   [app.main.ui.icons :as i]
   [app.main.ui.keyboard :as kbd]
   [app.main.data.modal :as modal]
   [app.util.dom :as dom]
   [app.util.i18n :as i18n :refer [t tr]]
   [app.util.object :as obj]
   [app.util.router :as rt]
   [app.util.time :as dt]
   [beicon.core :as rx]
   [cljs.spec.alpha :as s]
   [cuerdas.core :as str]
   [goog.functions :as f]
   [okulary.core :as l]
   [rumext.alpha :as mf]))

(mf/defc sidebar-project-edition
  [{:keys [item on-end] :as props}]
  (let [name      (mf/use-state (:name item))
        input-ref (mf/use-ref)

        on-input
        (mf/use-callback
         (fn [event]
           (->> event
                (dom/get-target)
                (dom/get-value)
                (reset! name))))

        on-cancel
        (mf/use-callback
         (fn []
           (st/emit! dd/clear-project-for-edit)
           (on-end)))

        on-keyup
        (mf/use-callback
         (fn [event]
           (cond
             (kbd/esc? event)
             (on-cancel)

             (kbd/enter? event)
             (let [name (-> event
                            dom/get-target
                            dom/get-value)]
               (st/emit! dd/clear-project-for-edit
                         (dd/rename-project (assoc item :name name)))
               (on-end)))))]

    (mf/use-effect
     (fn []
       (let [node (mf/ref-val input-ref)]
         (dom/focus! node)
         (dom/select-text! node))))

    [:div.edit-wrapper
     [:input.element-title {:value @name
                            :ref input-ref
                            :on-change on-input
                            :on-key-down on-keyup}]
     [:span.close {:on-click on-cancel} i/close]]))



(mf/defc sidebar-project
  [{:keys [item selected?] :as props}]
  (let [dstate    (mf/deref refs/dashboard-local)
        edit-id   (:project-for-edit dstate)

        edition?  (mf/use-state (= (:id item) edit-id))

        on-click
        (mf/use-callback
         (mf/deps item)
         (fn []
           (st/emit! (rt/nav :dashboard-files {:team-id (:team-id item)
                                               :project-id (:id item)}))))
        on-dbl-click
        (mf/use-callback #(reset! edition? true))]

    [:li {:on-click on-click
          :on-double-click on-dbl-click
          :class (when selected? "current")}
     (if @edition?
       [:& sidebar-project-edition {:item item
                                    :on-end #(reset! edition? false)}]
       [:span.element-title (:name item)])]))


(mf/defc sidebar-search
  [{:keys [search-term team-id locale] :as props}]
  (let [search-term (or search-term "")

        emit! (mf/use-memo #(f/debounce st/emit! 500))

        on-search-focus
        (mf/use-callback
         (mf/deps team-id)
         (fn [event]
           (let [target (dom/get-target event)
                 value (dom/get-value target)]
             (dom/select-text! target)
             (if (empty? value)
               (emit! (rt/nav :dashboard-search {:team-id team-id} {}))
               (emit! (rt/nav :dashboard-search {:team-id team-id} {:search-term value}))))))

        on-search-change
        (mf/use-callback
         (mf/deps team-id)
         (fn [event]
           (let [value (-> (dom/get-target event)
                           (dom/get-value))]
             (emit! (rt/nav :dashboard-search {:team-id team-id} {:search-term value})))))

        on-clear-click
        (mf/use-callback
         (mf/deps team-id)
         (fn [event]
           (let [search-input (dom/get-element "search-input")]
             (dom/clean-value! search-input)
             (dom/focus! search-input)
             (emit! (rt/nav :dashboard-search {:team-id team-id} {})))))]

    [:form.sidebar-search
     [:input.input-text
      {:key :images-search-box
       :id "search-input"
       :type "text"
       :placeholder (t locale "ds.search.placeholder")
       :default-value search-term
       :auto-complete "off"
       :on-focus on-search-focus
       :on-change on-search-change
       :ref #(when % (set! (.-value %) search-term))}]
     [:div.clear-search
      {:on-click on-clear-click}
      i/close]]))

(mf/defc sidebar-team-switch
  [{:keys [team profile locale] :as props}]
  (let [show-dropdown? (mf/use-state false)

        show-team-opts-ddwn? (mf/use-state false)
        show-teams-ddwn?     (mf/use-state false)
        teams                (mf/use-state [])

        go-members
        (mf/use-callback
         (mf/deps team)
         (st/emitf (rt/nav :dashboard-team-members {:team-id (:id team)})))

        go-settings
        (mf/use-callback
         (mf/deps team)
         (st/emitf (rt/nav :dashboard-team-settings {:team-id (:id team)})))

        go-projects
        (mf/use-callback #(st/emit! (rt/nav :dashboard-projects {:team-id %})))

        on-create-clicked
        (mf/use-callback
         (st/emitf (modal/show :team-form {})))

        on-rename-clicked
        (mf/use-callback
         (mf/deps team)
         (st/emitf (modal/show :team-form {:team team})))

        leave-fn
        (mf/use-callback
         (mf/deps team)
         (constantly nil))

        on-leave-clicked
        (mf/use-callback
         (mf/deps team)
         (st/emitf (modal/show
                    {:type :confirm
                     :title "Leave team"
                     :message "Are you sure you want to leave this team?"
                     :accept-label "Delete team"
                     :on-accept leave-fn})))

        delete-fn
        (mf/use-callback
         (mf/deps team)
         (constantly nil))

        on-delete-clicked
        (mf/use-callback
         (mf/deps team)
         (st/emitf (modal/show
                    {:type :confirm
                     :title "Delete team"
                     :message "Are you sure you want to delete this team?"
                     :accept-label "Delete team"
                     :on-accept delete-fn})))]

    (mf/use-effect
     (mf/deps (:id team))
     (fn []
       (->> (rp/query! :teams)
            (rx/map #(mapv dd/assoc-team-avatar %))
            (rx/subs #(reset! teams %)))))

    [:div.sidebar-team-switch
     [:div.switch-content
      [:div.current-team
       (if (:is-default team)
         [:div.team-name
          [:span.team-icon i/logo-icon]
          [:span.team-text (t locale "dashboard.sidebar.default-team-name")]]
         [:div.team-name
          [:span.team-icon
           [:img {:src (cfg/resolve-media-path (:photo team))}]]
          [:span.team-text {:title (:name team)} (:name team)]])
       [:span.switch-icon {:on-click #(reset! show-teams-ddwn? true)}
        i/arrow-down]]
      (when-not (:is-default team)
        [:div.switch-options {:on-click #(reset! show-team-opts-ddwn? true)}
         i/actions])]

     ;; Teams Dropdown
     [:& dropdown {:show @show-teams-ddwn?
                   :on-close #(reset! show-teams-ddwn? false)}
      [:ul.dropdown.teams-dropdown
       [:li.title (t locale "dashboard.sidebar.switch-team")]
       [:hr]
       [:li.team-name {:on-click (partial go-projects (:default-team-id profile))}
        [:span.team-icon i/logo-icon]
        [:span.team-text "Your penpot"]]

       (for [team (remove :is-default @teams)]
         [:* {:key (:id team)}
          [:hr]
          [:li.team-name {:on-click (partial go-projects (:id team))}
           [:span.team-icon
            [:img {:src (cfg/resolve-media-path (:photo team))}]]
           [:span.team-text {:title (:name team)} (:name team)]]])

       [:hr]
       [:li.action {:on-click on-create-clicked}
        (t locale "dashboard.sidebar.create-team")]]]

     [:& dropdown {:show @show-team-opts-ddwn?
                   :on-close #(reset! show-team-opts-ddwn? false)}
      [:ul.dropdown.options-dropdown
       [:li {:on-click go-members} (t locale "dashboard.sidebar.team-members")]
       [:li {:on-click go-settings} (t locale "dashboard.sidebar.team-settins")]
       [:hr]
       [:li {:on-click on-rename-clicked} (t locale "dashboard.sidebar.rename-team")]
       [:li {:on-click on-leave-clicked}  (t locale "dashboard.sidebar.leave-team")]
       [:li {:on-click on-delete-clicked} (t locale "dashboard.sidebar.delete-team")]]]
     ]))

(mf/defc sidebar-content
  [{:keys [locale projects profile section team project search-term] :as props}]
  (let [default-project-id
        (->> (vals projects)
             (d/seek :is-default)
             (:id))

        projects?   (= section :dashboard-projects)
        libs?       (= section :dashboard-libraries)
        drafts?     (and (= section :dashboard-files)
                         (= (:id project) default-project-id))

        go-projects #(st/emit! (rt/nav :dashboard-projects {:team-id (:id team)}))
        go-default  #(st/emit! (rt/nav :dashboard-files {:team-id (:id team) :project-id default-project-id}))
        go-libs     #(st/emit! (rt/nav :dashboard-libraries {:team-id (:id team)}))

        pinned-projects
        (->> (vals projects)
             (remove :is-default)
             (filter :is-pinned))]

    [:div.sidebar-content
     [:& sidebar-team-switch {:team team :profile profile :locale locale}]

     [:hr]
     [:& sidebar-search {:search-term search-term
                         :team-id (:id team)
                         :locale locale}]
     [:div.sidebar-content-section
      [:ul.sidebar-nav.no-overflow
       [:li.recent-projects
        {:on-click go-projects
         :class-name (when projects? "current")}
        i/recent
        [:span.element-title (t locale "dashboard.sidebar.projects")]]

       [:li {:on-click go-default
             :class-name (when drafts? "current")}
        i/file-html
        [:span.element-title (t locale "dashboard.sidebar.drafts")]]


       [:li {:on-click go-libs
             :class-name (when libs? "current")}
        i/library
        [:span.element-title (t locale "dashboard.sidebar.libraries")]]]]

     [:hr]

     [:div.sidebar-content-section
      (if (seq pinned-projects)
        [:ul.sidebar-nav
         (for [item pinned-projects]
           [:& sidebar-project
            {:item item
             :key (:id item)
             :id (:id item)
             :selected? (= (:id item) (:id project))}])]
        [:div.sidebar-empty-placeholder
         [:span.icon i/pin]
         [:span.text (t locale "dashboard.sidebar.no-projects-placeholder")]])]]))


(mf/defc profile-section
  [{:keys [profile locale] :as props}]
  (let [show  (mf/use-state false)
        photo (:photo-uri profile "")
        photo (if (str/empty? photo)
                "/images/avatar.jpg"
                photo)

        on-click
        (mf/use-callback
         (fn [section event]
           (dom/stop-propagation event)
           (if (keyword? section)
             (st/emit! (rt/nav section))
             (st/emit! section))))]

    [:div.profile-section {:on-click #(reset! show true)}
     [:img {:src photo}]
     [:span (:fullname profile)]

     [:& dropdown {:on-close #(reset! show false)
                   :show @show}
      [:ul.dropdown
       [:li {:on-click (partial on-click :settings-profile)}
        [:span.icon i/user]
        [:span.text (t locale "dashboard.header.profile-menu.profile")]]
       [:hr]
       [:li {:on-click (partial on-click :settings-password)}
        [:span.icon i/lock]
        [:span.text (t locale "dashboard.header.profile-menu.password")]]
       [:hr]
       [:li {:on-click (partial on-click da/logout)}
        [:span.icon i/exit]
        [:span.text (t locale "dashboard.header.profile-menu.logout")]]]]]))

(mf/defc sidebar
  {::mf/wrap-props false
   ::mf/wrap [mf/memo]}
  [props]
  (let [locale  (mf/deref i18n/locale)
        profile (mf/deref refs/profile)
        props   (-> (obj/clone props)
                    (obj/set! "locale" locale)
                    (obj/set! "profile" profile))]

    [:div.dashboard-sidebar
     [:div.sidebar-inside
      [:> sidebar-content props]
      [:& profile-section {:profile profile
                           :locale locale}]]]))


