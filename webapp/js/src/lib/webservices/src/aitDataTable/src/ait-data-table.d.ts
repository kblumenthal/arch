import { LitElement } from "lit";
import { ApiConstructorType, DataTable as DataTableType, DataTableEvent } from "./types.js";
type PropsT<RowT> = DataTableType<RowT>["props"];
export declare class AitDataTable<RowT> extends LitElement {
    actionButtonClasses: PropsT<RowT>["actionButtonClasses"];
    actionButtonLabels: PropsT<RowT>["actionButtonLabels"];
    actionButtonDisabled: PropsT<RowT>["actionButtonDisabled"];
    actionButtonSignals: PropsT<RowT>["actionButtonSignals"];
    apiBaseUrl: PropsT<RowT>["apiBaseUrl"];
    apiFactory: ApiConstructorType<RowT>;
    apiCollectionEndpoint: PropsT<RowT>["apiCollectionEndpoint"];
    apiStaticParamPairs: PropsT<RowT>["apiStaticParamPairs"];
    cellRenderers: PropsT<RowT>["cellRenderers"];
    columnHeaders: PropsT<RowT>["columnHeaders"];
    columns: PropsT<RowT>["columns"];
    columnSortParamMap: PropsT<RowT>["columnSortParamMap"];
    filterableColumns: PropsT<RowT>["filterableColumns"];
    columnFilterDisplayMaps: PropsT<RowT>["columnFilterDisplayMaps"];
    columnFilterParams: PropsT<RowT>["columnFilterParams"];
    noInitialSearch: PropsT<RowT>["noInitialSearch"];
    nonSelectionActionLabels: PropsT<RowT>["nonSelectionActionLabels"];
    nonSelectionActions: PropsT<RowT>["nonSelectionActions"];
    noResultsText: PropsT<RowT>["noResultsText"];
    nullString: PropsT<RowT>["nullString"];
    pageLength: PropsT<RowT>["pageLength"];
    pluralName: PropsT<RowT>["pluralName"];
    persistSearchStateInUrl: PropsT<RowT>["persistSearchStateInUrl"];
    props: PropsT<RowT> | Record<string, unknown>;
    rowClickEnabled: PropsT<RowT>["rowClickEnabled"];
    rowIdColumn: PropsT<RowT>["rowIdColumn"];
    searchColumns: PropsT<RowT>["searchColumns"];
    searchColumnLabels: PropsT<RowT>["searchColumnLabels"];
    selectAllExtraQueryParams: PropsT<RowT>["selectAllExtraQueryParams"];
    selectable: PropsT<RowT>["selectable"];
    singleName: PropsT<RowT>["singleName"];
    sort: PropsT<RowT>["sort"];
    sortableColumns: PropsT<RowT>["sortableColumns"];
    dataTable: DataTableType<RowT>;
    static styles: import("lit").CSSResult[];
    render(): HTMLElement | Array<HTMLElement>;
    nonSelectionActionHandler(action: string): void;
    selectionActionHandler(action: string, selectedRows: Array<RowT>): void;
    postSelectionChangeHandler(selectedRows: Array<RowT>): void;
    dataTableEventReceiver(e: DataTableEvent<RowT>): void;
}
export {};
