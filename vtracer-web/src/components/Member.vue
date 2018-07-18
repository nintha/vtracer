<template>
<div>
  <transition name="slide-fade">
    <div v-if="mainTab" key="traceMember">
        <Button type="primary" @click="renderModal">Trace new member</Button>
        <br/> <br/>
        <Table stripe :loading="loading" :columns="columns" :data="data"></Table>
        <br/>
        <Page :total="total" :page-size="pageSize" :current="page" show-total class="paging" @on-change="fetchData"></Page>
    </div>
    <div v-else key="memberInfo">
        <Button type="primary" @click="mainTab=true">Back</Button>
        <br/> <br/>
        <MemberInfo :record="record"></MemberInfo>
    </div>
  </transition>
</div>
</template>
<script>
import MemberInfo from './MemberInfo.vue'
export default {
  components: {
    MemberInfo
  },
  data() {
    return {
      mainTab: true,
      record: {},
      loading: true,
      columns: [
        {
          title: 'Name',
          key: 'name',
          render: (h, data) => {
            return h('div', [
              h('img', {
                attrs: { src: data.row.face, height: '40px' },
                style: { verticalAlign: 'middle', marginRight: '5px', cursor: 'pointer' },
                on: {
                  click: () => window.open(`https://space.bilibili.com/${data.row.mid}#/`, '_blank')
                }
              }),
              h('span', {}, data.row.name)
            ])
          }
        },
        {
          title: 'Mid',
          key: 'mid'
        },
        {
          title: 'Record Time',
          key: 'ctime'
        },
        {
          title: 'Action',
          key: 'action',
          width: 240,
          align: 'center',
          render: (h, params) => {
            return h('div', [
              h(
                'Button',
                {
                  props: {
                    type: 'primary',
                    size: 'small'
                  },
                  style: {
                    marginRight: '5px'
                  },
                  on: {
                    click: () => this.memberInfo(params.row)
                  }
                },
                'View'
              ),
              h(
                'Button',
                {
                  props: {
                    type: 'error',
                    size: 'small'
                  },
                  style: {
                    marginRight: '5px'
                  },
                  on: {
                    click: () => this.deleteMember(params.row)
                  }
                },
                'Delete'
              )
            ])
          }
        }
      ],
      data: [],
      page: 1,
      total: 0,
      pageSize: 20
    }
  },
  methods: {
    fetchData(page = 1) {
      this.page = page
      this.$api.get(`/trace/member/page/${page}`, {}, r => {
        this.data = r.data.list
        this.total = r.data.total
        this.pageSize = r.data.pageSize
        this.loading = false
      })
    },
    renderModal() {
      const vee = this
      let tempValue
      this.$Modal.confirm({
        render: h => {
          return h('Input', {
            props: {
              autofocus: true,
              placeholder: 'Please enter mid'
            },
            on: {
              input: val => (tempValue = val)
            }
          })
        },
        onOk() {
          vee.addMember(tempValue)
        }
      })
    },
    addMember(mid) {
      if (Number.isInteger(Number(mid)) == false) {
        this.$Notice.error({
          title: `Oops! "${mid}" is invalid mid`
        })
        return
      }
      this.$api.post(`/trace/member/${mid}`, {}, r => {
        if (r.data.effect > 0) {
          this.fetchData()
          this.$Notice.success({
            title: `Great! we are tracing ${mid}`
          })
        } else {
          this.$Notice.error({
            title: `Oops! ${mid} is dups or invalid`
          })
        }
      })
    },
    deleteMember({ mid, name }) {
      this.$Modal.confirm({
        title: 'Delete Member',
        content: `<h4>Really to delete ${name}[${mid}] ?</h4>
                  <p>*删除后追踪数据不再保留</p>`,
        onOk: () => {
          this.$api.delete(`/trace/member/${mid}`, {}, r => {
            if (r.data.effect > 0) {
              this.fetchData()
              this.$Notice.success({
                title: `Yoo! ${name} gone.`
              })
            } else {
              this.$Notice.error({
                title: `Oops! ${name} is still here.`
              })
            }
          })
        },
        onCancel: () => {
          // this.$Message.info('nothing happen.')
        }
      })
    },
    memberInfo(record) {
      this.record = record
      this.mainTab = false
    }
  },
  created() {
    this.fetchData()
  }
}
</script>
