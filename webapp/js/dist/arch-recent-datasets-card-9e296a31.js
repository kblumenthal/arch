import{g as t,d as a,i as e,_ as s,s as r,y as d,a as i}from"./chunk-styles-690ba0e4.js";import{t as l}from"./chunk-arch-alert-46aea875.js";import{P as c}from"./chunk-helpers-6405a525.js";import{i as o}from"./chunk-helpers-139f8162.js";import"./chunk-arch-card-2f07a64a.js";import"./chunk-arch-loading-indicator-02b42d35.js";import"./arch-sub-collection-builder-2c0fad15.js";import"./arch-generate-dataset-form-e492768c.js";import"./chunk-types-df9f20ed.js";var n,h=[t,a,e`
    thead > tr.hidden-header {
      color: transparent;
    }

    th.date {
      width: 8rem;
      text-align: right;
    }

    td.name,
    td.collection {
      text-overflow: ellipsis;
      white-space: nowrap;
      overflow-x: hidden;
    }

    td.date {
      text-align: right;
    }
  `];let p=n=class extends r{constructor(){super(),this.datasets=void 0,this.initDatasets()}render(){var t,a;const{numDisplayedDatasets:e}=n,s=void 0===this.datasets,r=(null!==(t=this.datasets)&&void 0!==t?t:[]).length>0,i=null!==(a=this.datasets)&&void 0!==a?a:[];return d`
      <arch-card
        title="Recent Datasets"
        ctatext="Generate New Dataset"
        ctahref="/datasets/generate"
      >
        <div slot="content">
          <table>
            <thead>
              <tr class="${s||!r?"hidden-header":""}">
                <th class="name">Dataset</th>
                <th class="collection">Collection Name</th>
                <th class="date">Date Generated</th>
              </tr>
            </thead>
            <tbody>
              ${s?[d`<tr>
              <td colspan="3">
                <arch-loading-indicator></arch-loading-indicator>
              </td>
            </tr>`]:r?i.slice(0,e).map((t=>{const a=`${t.name}${-1!==t.sample?" (Sample)":""}`;return d`
              <tr>
                <td class="name">
                  <a
                    href="${c.dataset(t.id,t.sample)}"
                    title="${a}"
                  >
                    ${a}
                  </a>
                </td>
                <td class="collection" title="${t.collectionName}">
                  ${t.collectionName}
                </td>
                <td class="date">
                  ${o(t.finishedTime)}
                </td>
              </tr>
            `})):[d`<tr>
              <td colspan="3"><i>New datasets will be listed here.</i></td>
            </tr>`]}
            </tbody>
          </table>
        </div>
        <div slot="footer">
          ${s||!r?d``:d`
                <a href="/datasets/explore" class="view-all">
                  View
                  ${i.length>e?d`All ${i.length}`:d``}
                  Datasets
                </a>
              `}
        </div>
      </arch-card>
    `}async initDatasets(){this.datasets=await(await fetch("/api/datasets?state=Finished")).json()}};p.numDisplayedDatasets=10,p.styles=h,s([l()],p.prototype,"datasets",void 0),p=n=s([i("arch-recent-datasets-card")],p);export{p as ArchRecentDatasetsCard};
//# sourceMappingURL=arch-recent-datasets-card-9e296a31.js.map
