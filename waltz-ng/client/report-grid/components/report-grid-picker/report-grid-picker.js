import template from "./report-grid-picker.html";
import {initialiseData} from "../../../common";
import {CORE_API} from "../../../common/services/core-api-utils";

const bindings = {
    parentEntityRef: "<",
    onGridSelect: "<",
    onRoadmapSelect: "<"
};

const initData = {
};

function controller(serviceBroker) {

    const vm = initialiseData(this, initData);

    vm.$onChanges = () => {
        serviceBroker
            .loadViewData(CORE_API.ReportGridStore.findAll)
            .then(r => vm.grids = r.data);
    };

    vm.onSelectGrid = (grid) => {
        vm.selectedGridId = grid.id;
        vm.onGridSelect(grid);
    };

    vm.onSelectRoadmap = () => {
        vm.onRoadmapSelect();
    };

}

controller.$inject = ["ServiceBroker"];

const component = {
    controller,
    bindings,
    template
};

export default {
    id: "waltzReportGridPicker",
    component,
}