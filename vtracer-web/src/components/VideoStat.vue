<template>
  <div>
    <h2>{{record.title}}</h2>
    <br>
    <div>
      <Switch size="small" v-model="enableYAxisDataMin" @on-change="changeYAxisMinType" />
      <strong title="停用/启用: 以 0/数据中的最小值 作为Y轴起点"> Reactive YAxis </strong> 
    </div>
    <div :style="{position:'relative'}">            
      <LineChart :opt="opt"></LineChart>
      <Spin fix v-if="spinShow">
        <Icon type="load-c" size=18 class="demo-spin-icon-load"></Icon>
        <div>Loading</div>
      </Spin>
    </div>
    <br>
    <DateSlider :startTime="originalDateValue[0]" :endTime="originalDateValue[1]" :onchange="sliderChanged"></DateSlider>
  </div>
</template>
<style>
.demo-spin-icon-load {
  animation: ani-demo-spin 1s linear infinite;
}
</style>
<script>
import LineChart from './LineChart.vue'
import DateSlider from './DateSlider.vue'
import moment from 'moment'
import Vue from 'vue'

const KEY_ENABLE_YAXIS_DATA_MIN = "VideoStat-EnableYAxisDataMin";
export default {
  components: { LineChart, DateSlider },
  props: {
    record: Object
  },
  data() {
    const et = +moment()
    return {
      spinShow: true,
      originalDateValue: [this.record.ctime, et],
      dateValue: [this.record.ctime, et],
      opt: {},
      enableYAxisDataMin: false
    }
  },
  mounted() {
    this.enableYAxisDataMin = localStorage.getItem(KEY_ENABLE_YAXIS_DATA_MIN) === 'true'
    this.fetchVideoStat(this.dateValue)
  },
  methods: {
    fetchVideoStat(timerange) {
      this.spinShow = true
      const url = `/trace/videoStat/${this.record.aid}`
      this.$api.get(url, { st: timerange[0], et: timerange[1] }, r => {
        if (r.data.list) {
          this.opt = this.createOpt(r.data.list)
          this.spinShow = false
        }
      })
    },
    createOpt(videoStatList) {
      const otherFields = ['coin', 'share', 'danmaku', 'favorite', 'reply', 'like', 'dislike', 'online']
      const fields = ['view', ...otherFields]
      // 如果卡可以再进行性能优化，如减少遍历次数
      const viewMax = videoStatList.map(v => v.view).reduce((a,b) => a > b ? a : b)
      const otherMax = videoStatList.flatMap(v => otherFields.map(f=>v[f])).reduce((a,b) => a > b ? a : b)
      let obj = {
        tooltip: {
          trigger: 'axis',
          formatter: dataSet => {
            const getSortedValue = data => data.name == 'view' ? data.value[1] / viewMax : data.value[1] / otherMax;
            dataSet.sort( (left, right) => getSortedValue(right) - getSortedValue(left) )
            let str = `${moment(dataSet[0].value[0]).format('YYYY-MM-DD HH:mm:ss')}`
            for(let i=0; i < dataSet.length; i++){
              str += `<br/>${dataSet[i].marker} ${dataSet[i].name}: ${dataSet[i].value[1]}`
            }
            return str
          }
        },
        legend: {
          data: fields
        },
        xAxis: {
          type: 'time',

          splitLine: {
            show: true
          }
        },
        yAxis: [
          {
            name: 'view',
            type: 'value',
            max: 'dataMax',
            min: this.enableYAxisDataMin ? 'dataMin' : null
          },
          {
            name: 'other',
            type: 'value',
            max: 'dataMax',
            min: this.enableYAxisDataMin ? 'dataMin' : null
          }
        ],
        series: fields.map(field => {
          return {
            name: field,
            type: 'line',
            showSymbol: false,
            hoverAnimation: false,
            yAxisIndex: field == 'view' ? 0 : 1,
            data: videoStatList.filter(v => v[field] > 0).map(v => {
              return {
                name: field,
                value: [v.ctime, v[field]]
              }
            })
          }
        })
      }
      return obj
    },
    sliderChanged(left, right) {
      // console.log(left,right)
      this.dateValue = [ +moment(left), +moment(right) ]
      this.fetchVideoStat(this.dateValue)
    },
    changeYAxisMinType(status){
      localStorage.setItem(KEY_ENABLE_YAXIS_DATA_MIN, status)
      this.fetchVideoStat(this.dateValue)
    },
    str2timestamp(str){
      return +moment(str)
    },
    timestamp2str(arr){
      return arr.map(v => moment(v).format('YYYY-MM-DD HH:mm:ss'))
    }
  }
}
</script>
