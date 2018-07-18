<template>
  <div>
    <div :style="{float:'right', marginRight:'50px'}">
    <DatePicker type="datetimerange" 
                placeholder="Select date and time" 
                style="width: 300px"
                :value="dateValue"
                :disabled="true"
                @on-change="fetchMemberInfo"></DatePicker>
    </div>
    <h2>{{record.name}}</h2>
    <div :style="{position:'relative'}">            
      <LineChart :opt="opt"></LineChart>
      <Spin fix v-if="spinShow">
        <Icon type="load-c" size=18 class="demo-spin-icon-load"></Icon>
        <div>Loading</div>
      </Spin>
    </div>
    <br>
    <DateSlider :startTime="str2timestamp(dateValue[0])" :endTime="str2timestamp(dateValue[1])" :onchange="sliderChanged"></DateSlider>
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

export default {
  components: { LineChart, DateSlider },
  props: {
    record: Object
  },
  data() {
    return {
      spinShow: true,
      dateValue: [this.record.ctime, moment().format('YYYY-MM-DD HH:mm:ss')],
      opt: {}
    }
  },
  mounted() {
    this.fetchMemberInfo(this.dateValue)
  },
  methods: {
    // 分块加载数据
    rangeFetch(timerange) {
      const aaa = moment()
      this.spinShow = true
      const stepHours = 36
      const wrapList = []
      const diffhours = moment(timerange[1]).diff(moment(timerange[0]), 'hours', true)
      let latch = Math.ceil(diffhours / stepHours)
      // console.log('latch total:', latch)
      for (let st = moment(timerange[0]); st.isBefore(moment(timerange[1])); ) {
        ;(() => {
          const stStr = st.format('YYYY-MM-DD HH:mm:ss')
          const etStr = st.add(stepHours, 'hours').format('YYYY-MM-DD HH:mm:ss')

          const sublist = []
          wrapList.push(sublist)
          const url = `/trace/memberInfo/${this.record.mid}`
          this.$api.get(url, { st: stStr, et: etStr }, r => {
            if (r.data.list) {
              r.data.list.forEach(v => sublist.push(v))
            }
            latch--
            // console.log('latch rest:', latch)
          })
        })()
      }

      const flag = setInterval(() => {
        if (latch <= 0) {
          const rslist = []
          wrapList.forEach(v => rslist.push(...v))
          this.opt = this.createOpt(rslist)
          clearInterval(flag)
          this.spinShow = false
          console.log('cost', moment().diff(aaa))
        }
      }, 100)
    },
    fetchMemberInfo(timerange) {
      // this.rangeFetch(timerange)
      this.spinShow = true
      const url = `/trace/memberInfo/${this.record.mid}`
      this.$api.get(url, { st: timerange[0], et: timerange[1] }, r => {
        if (!r.data.list) return

        this.opt = this.createOpt(r.data.list)
        this.spinShow = false
      })
    },
    createOpt(dataList) {
      const fields = ['fans','archiveView']
      let obj = {
        tooltip: {
          trigger: 'axis',
          formatter: v => {
            let str = `${v[0].value[0]}`
            for(let i=0; i < v.length; i++){
              str += `<br/>${v[i].marker} ${v[i].name}: ${v[i].value[1]}`
            }
            return str
          }
        },
        legend: {
          data: fields
        },
        xAxis: {
          type: 'time',
          // maxInterval: 3600 * 12 * 1000,
          splitLine: {
            show: true
          }
        },
        yAxis: [
          {
            name: 'fans',
            type: 'value',
            min: 'dataMin'
          },
          {
            name: 'archiveView',
            type: 'value',
            min: 'dataMin'
          },
          
        ],
        series: fields.map(field => {
          return {
            name: field,
            type: 'line',
            showSymbol: false,
            hoverAnimation: false,
            yAxisIndex: field == 'fans' ? 0 : 1,
            data: dataList.map(v => {
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
      const timeranger = [
        moment(left).format('YYYY-MM-DD HH:mm:ss'),
        moment(right).format('YYYY-MM-DD HH:mm:ss')
      ]
      this.fetchMemberInfo(timeranger)
    },
    str2timestamp(str) {
      return +moment(str)
    }
  }
}
</script>
