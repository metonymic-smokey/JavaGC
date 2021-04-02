<template>
    <div class="content">
        <div id="loadingPage" v-show="isLoading">
            <div v-if="anttracksMode">
                <div class="d-flex justify-content-center">
                    <div class="spinner-border ml-auto mr-auto" role="status" aria-hidden="true"></div>
                </div>
                <strong>Loading...</strong>
            </div>
            <div>
                <div v-if="!anttracksMode">
                    <hr>
                    <h3>Load local data</h3>
                    <input id="fileSelector" class="btn btn-info" type="file" multiple @change="loadDataFromDisk">
                    <hr>
                    <h3>Load example data</h3>
                    <b><a href="https://community.dynatrace.com/community/display/DL/Demo+Applications+-+easyTravel">Dynatrace easyTravel</a> (SSP 2020 example)</b>:<br/>
                    Backend server with memory leak (Heap objects grouped by <em>type</em> and <em>closest domain call site</em>)
                    <br/>
                    <button id="easyTravelButton" class="btn btn-info" @click="loadEasyTravelData">Load</button>
                    <br/>
                    <br/>
                    <b><a href="https://mvnrepository.com/artifact/commons-httpclient/commons-httpclient/3.0.1">Commons HttpClient Version 3.0.1</a> (STAG 2020 example #1)</b>:<br/>
                    HTTP request library with memory leak (Heap objects grouped by <em>type</em> and <em>allocation site</em>)
                    <br>
                    <button id="commonsHttpButton" class="btn btn-info" @click="loadCommonsHttpData">Load</button>
                    <br/>
                    <br/>
                    <b><a href="https://community.dynatrace.com/community/display/DL/Demo+Applications+-+easyTravel">Dynatrace easyTravel</a> (STAG 2020 example #2)</b>:<br/>
                    Backend server with memory leak (Heap objects grouped by <em>containing data structure</em>, <em>type</em> and <em>closest domain call site</em>)
                    <br>
                    <button id="easyTravelDSButton" class="btn btn-info" @click="loadEasyTravelDSData">Load</button>
                    <hr/>
                    <h5><b><i>Please wait a few moments after clicking one of the load buttons until the visualization is shown</i></b></h5>
                    <h5><b><i>Data has to be fetched from the server and has to be processed afterwards</i></b></h5>
                </div>
            </div>
        </div>

        <div id="analysisPage" v-show="!isLoading">
            <div class="row top">
                <div id="absoluteChart" class="col-6 left">
                    <!-- svg injected via d3 in AbsoluteMemoryChart -->
                </div>
                <div class="col-6 left">
                    <div class="">
                        <strong>Current Heap:</strong>
                        Number: {{ manager ? manager.curDisplayedTreeIdx : "-" }} |
                        Time: {{ manager ? manager.curTrees[manager.curDisplayedTreeIdx].time.toLocaleString('en') : "-" }}ms |
                        Size: {{ manager ? currentHeapSize() : "-" }}
                    </div>
                    <div class="timeSliderContainer ">
                        <!-- slider injected via d3 in VisualizationManager -->
                    </div>
                    <div class="">
                        <button type="button" class="btn btn-primary mx-1" id="previousDataBtn">Previous heap</button>
                        <button type="button" class="btn btn-primary mx-1" id="nextDataBtn"> Next heap</button>
                    </div>
                    <div class="sep-row"></div>
                    <div id="metricSelection" class="">
                        <strong>Displayed Metric: </strong>
                        <!-- VisualizationManager grabs these inputs and registers listeners -->
                        <label>Bytes <input type="radio" name="displayedMetric" value="bytes" checked/></label>
                        <label>Objects <input type="radio" name="displayedMetric" value="objects"/> </label>
                    </div>
                    <div id="sortingModeContainer" class="">
                        <strong>Sorting Mode: </strong>
                        <!-- VisualizationManager grabs this select and registers listener -->
                        <select id="sortingModeSelection">
                            <option value="1">Absolute Growth</option>
                            <option value="2">Start Size</option>
                            <option value="3">End Size</option>
                        </select>
                    </div>
                    <div class="btn-group " role="group">
                        <button type="button" class="btn btn-info" id="navigateSunburst">Sunburst</button>
                        <button type="button" class="btn btn-info" id="navigateIcicle">Icicle</button>
                        <button type="button" class="btn btn-info" id="navigateTreemap">Treemap</button>
                        <button type="button" class="btn btn-info" id="navigateBarChart">Stacked Bar chart</button>
                    </div>
                </div>
            </div>
            <div class="sep-row"></div>
            <div id="breadcrumbContainer">
                <!-- breadcrumb injected via d3 in VisualizationManager -->
            </div>
            <div id="selectionAlert" class="alert alert-warning d-none" role="alert">
                <!-- accessed from AlertManager -->
                <span>Previously selected node </span>
                <strong class="oldNode">oldNode</strong>
                <span> does not exist in the in this tree, thus the closest ancestor </span>
                <strong class="newNode">newNode</strong>
                <span> was selected!</span>
                <button id="selectionAlertCloseBtn" type="button" class="close" aria-label="Close">
                    <span aria-hidden="true">&times;</span>
                </button>
            </div>
            <div class="row bottom">
                <div class="col-6">
                    <div id="zoomableSunburst" class="sunburst"></div>
                    <div id="localIcicle" class="icicle"></div>
                    <div id="localTreemap" class="treemap"></div>
                </div>
                <div class="col-6">
                    <div id="globalSunburst" class="sunburst"></div>
                    <div id="globalIcicle" class="icicle"></div>
                    <div id="globalTreemap" class="treemap"></div>
                </div>
            </div>
            <div class="row stackedBarChartContainer bottom">
                <div class="col-10" id="stackedBarChart"></div>
                <div class="col-2" id="SBCModeSelection">
                    <p>Axis Scaling Mode: </p>
                    <div>
                        <input type="radio" name="scalingMode" value="local" id="localScalingMode" checked/>
                        <label class="btnGroupLabelNoBotMargin" for="localScalingMode">Local</label>
                    </div>
                    <div>
                        <input type="radio" name="scalingMode" value="global" id="globalScalingMode"/>
                        <label for="globalScalingMode">Global</label>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script lang="ts">
    import {Component, Vue} from "vue-property-decorator";
    import ClassificationTree from "@/dao/ClassificationTree";
    import VisualizationManager from "@/visualizations/VisualizationManager";
    import DataInitializer from "@/dao/DataInitializer";
    import Helpers from "@/visualizations/util/Helpers";

    const axios = require('axios').default;

    @Component
    export default class TreeVisualization extends Vue {
        anttracksMode = true; // Will be checked and adjusted in mounted()
        isLoading = true;
        manager: VisualizationManager | null = null

        private mounted() {
            const vm = this;

            const socket = new WebSocket("ws://localhost:8887");
            // Connection opened
            socket.onopen = function (event) {
                console.log("Websocket available, load data from AntTracks");
                (window as any).loadData = vm.handleData;
            };
            socket.onerror = function (event) {
                console.log("Websocket not available (error), load data from disk");
                vm.anttracksMode = false;
            };
        }

        public loadDataFromDisk(event: InputEvent) {
            const vm = this;
            const files = (event.target! as HTMLInputElement).files;

            if (files != null) {
                var nReadFiles = 0;
                const trees: ClassificationTree[] = [];
                for (const file of files) {
                    const reader = new FileReader();
                    reader.onload = function () {
                        trees.push(JSON.parse(<string>reader.result));
                        nReadFiles++;
                        if (nReadFiles == files.length) {
                            vm.handleData(trees);
                        }
                    };
                    reader.readAsText(file);
                }
            }
        }

        public loadEasyTravelData() {
            const vm = this;
            const trees: ClassificationTree[] = [];

            // This creates a 50MB app.js file, this is not acceptable.
            //var requiredEasyTravel = require.context('../assets/classification_trees/easytravel_backend/type_closestdomaincallsite', true, /\.json$/);
            //requiredEasyTravel.keys().forEach(key => {
            //    const fileContent = requiredEasyTravel(key);
            //    console.log(fileContent);
            //    trees.push(fileContent);
            //});

            vm.isLoading = false;

            const times = [147252, 163612, 180136, 199098, 214088, 226582, 244892, 259778, 281650, 295980, 309987, 325734, 349050, 366655, 380518, 392700, 408503, 422389, 430536, 447299, 462189, 476131, 491426, 505806];

            // get data asynchronously from server "public" folder
            axios.all(times.map((time: number) => axios.get(`classification_trees/easytravel_backend/type_closestdomaincallsite/${time}.json`)))
                 .then(axios.spread(function (...responses: any[]) {
                     // console.log(responses);
                     responses.forEach(response => trees.push(response.data));
                     console.log(trees);
                     vm.handleData(trees);
                 }));
        }

        public loadCommonsHttpData() {
            const vm = this;
            const trees: ClassificationTree[] = [];

            vm.isLoading = false;

            const times: number[] = [11717, 17802, 23950, 30007, 36080, 42194, 48262, 54335, 60446, 66526, 72657, 78751, 84832, 90950, 97068, 103162, 109254, 115357, 121436, 127600, 133706, 139792, 145905, 152031, 158131, 168679];

            // get data asynchronously from server "public" folder
            axios.all(times.map((time: number) => axios.get(`classification_trees/commons_http/type_allocationsite/${time}.json`)))
                 .then(axios.spread(function (...responses: any[]) {
                     // console.log(responses);
                     responses.forEach(response => trees.push(response.data));
                     console.log(trees);
                     vm.handleData(trees);
                 }));
        }

        public loadEasyTravelDSData() {
            const vm = this;
            const trees: ClassificationTree[] = [];

            vm.isLoading = false;

            const times: number[] = [148271, 177406, 204174, 226582, 252489, 286385, 307884, 333460, 366655, 389459, 411301, 429569, 452024, 476131, 497560, 524162, 553745, 582861, 606581, 627542, 655378, 661177];

            // get data asynchronously from server "public" folder
            axios.all(times.map((time: number) => axios.get(`classification_trees/easytravel_backend/containingdatastructure_type_closestdomaincallsite/${time}.json`)))
                 .then(axios.spread(function (...responses: any[]) {
                     // console.log(responses);
                     responses.forEach(response => trees.push(response.data));
                     console.log(trees);
                     vm.handleData(trees);
                 }));
        }


        private handleData(data: any[]) {
            const vm = this;
            vm.isLoading = false;

            // vm.isLoading switch causes the DOM to change. Wait for this change, and then init the visualizations
            vm.$nextTick(function () {
                // DOM updated
                vm.manager = new DataInitializer().initVisualizationManager(new VisualizationManager(), data);
            });
            /*
             setTimeout(() => {
             vm.manager = new DataInitializer().initVisualizationManager(new VisualizationManager(), data);
             }, 1000);
             */
        }

        private currentHeapSize() {
            const vm = this;
            return Helpers.getValueFromTree(vm.manager!.curTrees[vm.manager!.curDisplayedTreeIdx], vm.manager!.displayBytes).toLocaleString('en');
        }
    }
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style>

</style>
