<style scoped>
    .layout{
        border: 1px solid #d7dde4;
        background: #f5f7f9;
        position: relative;
        border-radius: 4px;
        min-width: 1024px;
        /* overflow: hidden; */
    }
    .layout-header-bar{
        background: #fff;
        box-shadow: 0 1px 1px rgba(0,0,0,.1);
    }
    .layout-logo-left{
        width: 90%;
        height: 30px;
        background: #5b6270;
        border-radius: 3px;
        margin: 15px auto;
    }
    .menu-icon{
        transition: all .3s;
    }
    .rotate-icon{
        transform: rotate(-90deg);
    }
    .menu-item span{
        display: inline-block;
        overflow: hidden;
        width: 69px;
        text-overflow: ellipsis;
        white-space: nowrap;
        vertical-align: bottom;
        transition: width .2s ease .2s;
    }
    .menu-item i{
        transform: translateX(0px);
        transition: font-size .2s ease, transform .2s ease;
        vertical-align: middle;
        font-size: 16px;
    }
    .collapsed-menu span{
        width: 0px;
        transition: width .2s ease;
    }
    .collapsed-menu i{
        transform: translateX(5px);
        transition: font-size .2s ease .2s, transform .2s ease .2s;
        vertical-align: middle;
        font-size: 22px;
    }
</style>
<template>
    <div class="layout">
        <Layout>
            <Sider ref="side1" breakpoint="md" hide-trigger collapsible :collapsed-width="0" v-model="isCollapsed">
                <Menu :class="menuitemClasses" :active-name="this.$route.params.subpage" theme="dark" width="auto" :open-names="['tracer']"  @on-select="onMenuSelect">
                    <Submenu name="tracer">
                        <template slot="title">
                            <Icon type="ios-navigate"></Icon>
                            Tracer
                        </template>
                        <MenuItem name="member">Member</MenuItem>
                        <MenuItem name="video">Video</MenuItem>
                        <MenuItem name="faq">FAQ</MenuItem>
                        <MenuItem name="little-nest">Little Nest (Beta)</MenuItem>
                    </Submenu>
                </Menu>
            </Sider>
            <Layout>
                <Header class="layout-header-bar">
                    <div :style="{float:'right', marginTop:'20px',lineHeight:'20px',fontSize:'16px'}">
                        <Dropdown @on-click="onLogout" placement="bottom-start">
                            <a href="javascript:void(0)">
                                {{showUsername()}}
                                <Icon type="arrow-down-b"></Icon>
                            </a>
                            <DropdownMenu slot="list">
                                <DropdownItem>logout</DropdownItem>
                            </DropdownMenu>
                        </Dropdown>
                    </div>
                    <div>
                        <h1>
                            <Icon @click.native="collapsedSider" :style="{margin: '0 20px 0 -20px'}" :class="rotateIcon" type="md-menu" size="24"></Icon>
                            Video Tracer of Bilibili
                        </h1>
                    </div>
                </Header>
                <Content :style="{padding: '16px'}">
                    <Card>
                        <div style="min-height: 80vh">
                            <PageSwitch :subpage="this.$route.params.subpage"></PageSwitch>
                        </div>
                    </Card>
                </Content>
            </Layout>
        </Layout>
    </div>
</template>
<script>
import PageSwitch from '../components/PageSwitch.vue'
const GUEST_NAME = '# Guest'
export default {
    components: { PageSwitch },
    data () {
        return {
            isCollapsed: false
        };
    },
    computed: {
        rotateIcon () {
            return [
                'menu-icon',
                this.isCollapsed ? 'rotate-icon' : ''
            ];
        },
        menuitemClasses () {
            return [
                'menu-item',
                this.isCollapsed ? 'collapsed-menu' : ''
            ]
        }
    },
    methods: {
        showUsername(){
            const username = sessionStorage.getItem('username')
            if(!username){
                this.$api.get('/userInfo',{},r=>{
                    sessionStorage.setItem('username', r.data.user || GUEST_NAME)
                })
            }
            return username || GUEST_NAME
        },
        onMenuSelect(name) {
            console.log('go to', name)
            this.$router.push('/tracer/' + name)
        },
        onLogout(name){
            this.$api.get('/logout',{},r=>{
                console.log('logout')
                sessionStorage.removeItem('username')
                this.$router.push('/')
            })
        },
        collapsedSider () {
            this.$refs.side1.toggleCollapse();
        }
    }
}
</script>