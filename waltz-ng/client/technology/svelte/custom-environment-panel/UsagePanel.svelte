<script>

    import UsageEdit from "./UsageEdit.svelte";
    import EnvironmentRemovalConfirmation from "./EnvironmentRemovalConfirmation.svelte";
    import UsageTree from "./UsageTree.svelte";
    import UsageTable from "./UsageTable.svelte";
    import {customEnvironmentStore} from "../../../svelte-stores/custom-environment-store";
    import MiniActions from "../../../common/svelte/MiniActions.svelte";
    import {getContext} from "svelte";
    import UsageAssetInfo from "./UsageAssetInfo.svelte";

    export let doCancel;
    export let application
    export let usages;
    export let environment;

    const showTableAction = {
        name: "Show table",
        icon: "table",
        description: "Click to switch to table view",
        handleAction: showTable
    }
    const showTreeAction = {
        name: "Show tree",
        icon: "sitemap",
        description: "Click to switch to tree view",
        handleAction: showTree
    }
    const editAction = {name: "Edit", icon: "pencil", description: "Click to edit environment", handleAction: showEdit}
    const removeAction = {
        name: "Remove",
        icon: "trash",
        description: "Click to delete this environment and its associations to servers and databases",
        handleAction: showRemove
    }

    const editorActions = {
        TREE: [showTableAction, editAction, removeAction],
        TABLE: [showTreeAction, editAction, removeAction],
        EDIT: [],
        REMOVE: []
    };

    const viewerActions = {
        TREE: [showTableAction],
        TABLE: [showTreeAction],
        EDIT: [],
        REMOVE: []
    };

    const Modes = {
        TREE: "TREE",
        TABLE: "TABLE",
        EDIT: "EDIT",
        REMOVE: "REMOVE"
    };

    let canEdit = getContext("canEdit");

    let activeMode = Modes.TREE;

    let selectedAsset;

    $: actions = $canEdit
        ? editorActions[activeMode]
        : viewerActions[activeMode];

    function cancel() {
        doCancel();
    }

    function showTable() {
        return activeMode = Modes.TABLE
    }

    function showTree() {
        return activeMode = Modes.TREE
    }

    function showRemove() {
        return activeMode = Modes.REMOVE
    }

    function showEdit() {
        return activeMode = Modes.EDIT;
    }

    function doRemove(environment) {
        return customEnvironmentStore.remove(environment);
    }

    function selectAsset(e) {
        if( !_.isNil(selectedAsset) && selectedAsset === e.detail){
            selectedAsset = null;
        } else{
            selectedAsset = e.detail;
        }
    }


</script>

<!--TREEMODE-->
{#if usages.length === 0 && activeMode !== Modes.EDIT && activeMode !== Modes.REMOVE}
    <div>No databases or servers have been associated to this environment.</div>
{:else if activeMode === Modes.TREE}
    <div class="row">
        <div class="col-md-6">
            <UsageTree on:select={selectAsset}
                       usages={usages}/>
        </div>
        <div class="col-md-6">
            <UsageAssetInfo asset={selectedAsset}/>
        </div>
    </div>
{:else if activeMode === Modes.TABLE}
    <UsageTable {usages}/>
{:else if activeMode === Modes.EDIT}
    <UsageEdit {environment}
               usages={usages || []}
               {application}
               doCancel={showTree}/>
{:else if activeMode === Modes.REMOVE}
    <EnvironmentRemovalConfirmation {environment}
                                    {doRemove}
                                    doCancel={showTree}/>
{/if}
<br>
<MiniActions {actions}/>

