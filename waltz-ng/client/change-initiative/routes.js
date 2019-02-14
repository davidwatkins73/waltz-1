/*
 * Waltz - Enterprise Architecture
 * Copyright (C) 2016, 2017 Waltz open source project
 * See README.md for more information
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import ChangeInitiativeView from "./pages/view/change-initiative-view";
import {CORE_API} from "../common/services/core-api-utils";
import ChangeInitiativeExternalIdView from "./pages/external-id-view/change-initiative-external-id-view";

const baseState = {
    url: "change-initiative"
};


const viewState = {
    url: "/{id:int}",
    views: { "content@": ChangeInitiativeView }
};


const viewByExternalIdState = {
    url: "/external-id/{externalId}",
    views: {
        "content@": ChangeInitiativeExternalIdView
    },
    resolve: { changeInitiatives: changeInitiativeResolver }
};



function changeInitiativeResolver(serviceBroker, $stateParams) {
    return serviceBroker
        .loadViewData(CORE_API.ChangeInitiativeStore.findByExternalId, [ $stateParams.externalId ])
        .then(r => r.data);
}

changeInitiativeResolver.$inject = ["ServiceBroker", "$stateParams"];




function setupRoutes($stateProvider) {
    $stateProvider
        .state("main.change-initiative", baseState)
        .state("main.change-initiative.view", viewState)
        .state("main.change-initiative.external-id", viewByExternalIdState);
}


setupRoutes.$inject = ["$stateProvider"];


export default setupRoutes;