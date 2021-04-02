<template>
<div id="app" class="container-fluid p-1">
    <TreeVisualization/>
</div>
</template>

<script lang="ts">
import {Component, Vue} from 'vue-property-decorator';
import TreeVisualization from '@/components/TreeVisualization.vue'; // @ is an alias to /src

@Component({
               components: {
                   TreeVisualization,
               },
           })
export default class App extends Vue {


}
</script>

<style>
html {
    background: url("assets/bg12.jpg") no-repeat center center fixed;
    -webkit-background-size: cover;
    -moz-background-size: cover;
    -o-background-size: cover;
    background-size: cover;

    overflow-y: scroll;
}

body {
    background-color: rgba(255, 255, 255, 0.0)
}

.container, .container-xl, .container-fluid {
    border: black solid 2px;
    font-family: 'Source Sans Pro', sans-serif;
    -webkit-font-smoothing: antialiased;
    -moz-osx-font-smoothing: grayscale;
    text-align: center;
    background: white;
}

.col > div, .col-6 > div {
    margin: 5px;
}

h1 {
    font-size: 1.9rem;
}

h2 {
    font-size: 1.75rem;
}

h3 {
    font-size: 1.6rem;
}

h4 {
    font-size: 1.45rem;
}

h5 {
    font-size: 1.3rem;
}

h6 {
    font-size: 1.15rem;
}

.sunburst path, .icicle rect, .treemap rect {
    stroke-width: 1;
    stroke: black;
}

#stackedBarChart rect {
    stroke-width: 2;
    stroke: black;
}

.horizontal-lines line {
    stroke: lightgrey;
    stroke-opacity: 0.7;
    stroke-width: 1;
    shape-rendering: crispEdges;
}

.button {
    fill: white;
}

.selected-button {
    fill: #80bdff;
}

.hovered-button {
    fill: lightyellow;
}

.top .left {
    border-right: 1px solid black;
}

.top .right {
    border-left: 1px solid black;
}

.sep-row {
    margin-top: 5px;
    margin-bottom: 5px;
    height: 1px;
    background: rgb(0, 0, 0);
    /*background: radial-gradient(closest-side, rgba(23, 162, 184, 0.4), white);*/
}

/* https://www.w3schools.com/howto/howto_js_rangeslider.asp */
.slider {
    -webkit-appearance: none;
    width: 100%;
    height: 15px;
    border-radius: 5px;
    background: #d3d3d3;
    outline: none;
    opacity: 0.7;
    -webkit-transition: .2s;
    transition: opacity .2s;
}

.slider::-webkit-slider-thumb {
    -webkit-appearance: none;
    appearance: none;
    width: 25px;
    height: 25px;
    border-radius: 50%;
    background: #4CAF50;
    cursor: pointer;
}

.slider::-moz-range-thumb {
    width: 25px;
    height: 25px;
    border-radius: 50%;
    background: #4CAF50;
    cursor: pointer;
}
</style>
