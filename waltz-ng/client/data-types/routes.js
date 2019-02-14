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

import {loadDataTypes, dataTypeByCodeResolver, dataTypeByIdResolver} from "./resolvers";

import HomePage from './pages/home/data-type-list';
import ViewPage from './pages/view/data-type-view';


const baseState = {
    resolve: {
        dataTypes: loadDataTypes
    }
};


const listState = {
    url: 'data-types',
    views: {'content@': HomePage }
};


const viewByCodeState = {
    url: 'data-types/code/{code}',
    views: {'content@': ViewPage },
    resolve: {dataType: dataTypeByCodeResolver }
};

const viewByExternalIdState = {
    url: 'data-types/external-id/{code}',
    views: {'content@': ViewPage },
    resolve: {dataType: dataTypeByCodeResolver }
};


const viewState = {
    url: 'data-types/{id:int}',
    views: {'content@': ViewPage },
    resolve: {dataType: dataTypeByIdResolver }
};


function setup($stateProvider) {
    $stateProvider
        .state('main.data-type', baseState)
        .state('main.data-type.list', listState)
        .state('main.data-type.code', viewByCodeState)
        .state('main.data-type.external-id', viewByExternalIdState)
        .state('main.data-type.view', viewState);
}

setup.$inject = ['$stateProvider'];


export default setup;