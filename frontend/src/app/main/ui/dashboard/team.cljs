;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; This Source Code Form is "Incompatible With Secondary Licenses", as
;; defined by the Mozilla Public License, v. 2.0.
;;
;; Copyright (c) 2020 UXBOX Labs SL

(ns app.main.ui.dashboard.team
  (:require
   [okulary.core :as l]
   [rumext.alpha :as mf]
   [app.common.exceptions :as ex]
   [app.main.constants :as c]
   [app.main.data.dashboard :as dd]
   [app.main.refs :as refs]
   [app.main.store :as st]
   [app.main.ui.icons :as i]
   [app.main.ui.keyboard :as kbd]
   [app.util.dom :as dom]
   [app.util.i18n :as i18n :refer [t tr]]
   [app.util.router :as rt]
   [app.util.time :as dt]))


(mf/defc header
  {::mf/wrap [mf/memo]}
  [{:keys [section locale team] :as props}]
  (let [go-members (mf/use-callback (mf/deps team) (st/emitf (rt/nav :dashboard-team-members {:team-id (:id team)})))
        go-settings (mf/use-callback (mf/deps team) (st/emitf (rt/nav :dashboard-team-settings {:team-id (:id team)})))

        members-section?  (= section :dashboard-team-members)
        settings-section? (= section :dashboard-team-settings)]
    [:header.dashboard-header
     [:h1.dashboard-title "Projects"]
     [:nav
      [:ul
       [:li {:class (when members-section? "active")}
        [:a {:on-click go-members} "MEMBERS"]]
       [:li {:class (when settings-section? "active")}
        [:a {:on-click go-settings} "SETTINGS"]]]]
     [:a.btn-secondary.btn-small
      (t locale "dashboard.header.new-project")]]))

(mf/defc team-members
  [{:keys [team] :as props}]
  (let [locale (mf/deref i18n/locale)]
    [:*
     [:& header {:locale locale
                 :section :dashboard-team-members
                 :team team}]
     [:section.dashboard-container.dashboard-team-members
      [:div.dashboard-table
       [:div.table-header
        [:div.table-field.name "Name"]
        [:div.table-field.email "Email"]
        [:div.table-field.permissions "Permissions"]]
       [:div.table-rows
        (for [i (range 10)]
          [:div.table-row {:key i}
           [:div.table-field.name (str "User name " i)]
           [:div.table-field.email (str "email" i "@example.com")]
           [:div.table-field.permissions
            (if (= i 0)
              [:span.label "Owner"]
              [:*
               [:span.label "Editor"]
               [:span.icon i/arrow-down]])]])]]]]))

(mf/defc team-settings
  [{:keys [team] :as props}]
  (let [locale (mf/deref i18n/locale)]
    [:*
     [:& header {:locale locale
                 :section :dashboard-team-settings
                 :team team}]
     [:section.dashboard-container.dashboard-team-settings]]))
