<template>
  <div class="bottom" id="echart" ref="mychart"> </div>
</template>
<style scoped>
#echart {
  height: 450px;
  width: 100%;
  margin: auto;
}
</style>


<script>
// echarts相关
let echarts = require('echarts/lib/echarts')
require('echarts/lib/chart/line')
require('echarts/lib/component/tooltip')
require('echarts/lib/component/toolbox')
require('echarts/lib/component/legend')
require('echarts/lib/component/markLine')
require('echarts/lib/component/dataZoom')

export default {
  data() {
    return {
      // myChart实例
      myChart: {}
    }
  },
  watch: {
    opt: {
      handler(newValue, oldValue) {
        if (!this.myChart) {
          this.setEchart()
        }
        this.chartChange()
      },
      deep: true
    }
  },
  props: {
    opt: Object
  },
  beforeDestroy() {
    this.myChart.clear()
  },
  mounted() {
    this.setEchart()
  },
  methods: {
    setEchart() {
      let dom = this.$refs.mychart
      this.myChart = echarts.init(dom)
      this.myChart.setOption(this.opt)
    },
    chartChange() {
      this.myChart.setOption(this.opt)
    }
  }
}
</script>
