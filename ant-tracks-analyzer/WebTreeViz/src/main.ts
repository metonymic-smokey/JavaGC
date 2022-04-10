import Vue from 'vue';
import App from './App.vue';

Vue.config.productionTip = false;

(window as any).vue = new Vue({
    render: (h) => h(App),
    created() {
        // `this` points to the vm instance
    }
}).$mount('#app');
