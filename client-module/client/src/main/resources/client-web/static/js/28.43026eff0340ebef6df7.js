webpackJsonp([28],{"8Wm6":function(s,a,e){"use strict";Object.defineProperty(a,"__esModule",{value:!0});var t=e("LPk9"),i=e("FJop"),l=e("x47x"),n={data:function(){var s=this;return{submitId:"aliasAliasing",address:this.$route.params.address,encrypted:this.$route.params.encrypted,usable:0,fee:0,allFee:1,aliasForm:{alias:""},aliasRules:{alias:[{validator:function(a,e,t){s.usable>=1.01?e&&/^(?!_)(?!.*?_$)[a-z0-9_]+$/.exec(e)?t():t(new Error(s.$t("message.c1041"))):t(new Error(s.$t("message.c107")))},trigger:"blur"}]}}},components:{Back:t.a,Password:i.a},mounted:function(){this.getBalanceAddress("/account/balance/"+this.address)},methods:{getBalanceAddress:function(s){var a=this;this.$fetch(s).then(function(s){if(s.success){var e=new l.BigNumber(1e-8);a.usable=parseFloat(e.times(s.data.usable).toString())}else a.usable=0})},countFee:function(){var s=this;if(""!==this.aliasForm.alias){var a="address="+this.address+"&alias="+this.aliasForm.alias;this.$fetch("/account/alias/fee?"+a).then(function(a){if(a.success){var e=new l.BigNumber(1e-8);s.fee=e.times(a.data.value);var t=new l.BigNumber(1);s.allFee=t.plus(s.fee)}})}},aliasingSubmit:function(s){var a=this;this.$refs[s].validate(function(s){s&&(a.encrypted?a.$refs.password.showPassword(!0):a.$confirm(a.$t("message.c164"),"",{confirmButtonText:a.$t("message.confirmButtonText"),cancelButtonText:a.$t("message.cancelButtonText")}).then(function(){var s={alias:a.aliasForm.alias,password:""};a.aliasing("/account/alias/"+a.address,s)}).catch(function(){}))})},toSubmit:function(s){if(this.$store.getters.getNetWorkInfo.localBestHeight===this.$store.getters.getNetWorkInfo.netBestHeight){var a={alias:this.aliasForm.alias,password:s};this.aliasing("/account/alias/"+this.address,a)}else this.$message({message:this.$t("message.c133")})},aliasing:function(s,a){var e=this;this.$post(s,a).then(function(s){s.success?(e.$message({type:"success",message:e.$t("message.passWordSuccess")}),e.$router.push({name:"/userInfo"})):e.$message({type:"warning",message:e.$t("message.passWordFailed")+s.msg})})}}},r={render:function(){var s=this,a=s.$createElement,e=s._self._c||a;return e("div",{staticClass:"edit-aliasing"},[e("Back",{attrs:{backTitle:this.$t("message.accountManagement")}}),s._v(" "),e("div",{staticClass:"edit-info"},[e("h2",[s._v(s._s(s.$t("message.c100")))]),s._v(" "),e("el-form",{ref:"aliasForm",attrs:{model:s.aliasForm,rules:s.aliasRules},nativeOn:{submit:function(s){s.preventDefault()}}},[e("div",{staticClass:"edit-aliasing-bg"},[e("p",[e("i"),s._v(s._s(s.$t("message.c103")))]),s._v(" "),e("p",[e("i"),s._v(s._s(s.$t("message.c170")))])]),s._v(" "),e("el-form-item",{staticClass:"label-aliasing",attrs:{label:s.$t("message.c102")+"：",prop:"alias"}},[e("el-input",{attrs:{disabled:!0},model:{value:this.address,callback:function(a){s.$set(this,"address",a)},expression:"this.address"}})],1),s._v(" "),e("el-form-item",{staticClass:"label-aliasing",attrs:{label:s.$t("message.c104")+"：",prop:"alias"}},[e("span",{staticClass:"yue"},[s._v(s._s(s.$t("message.currentBalance"))+": "+s._s(this.usable)+" NULS")]),s._v(" "),e("el-input",{staticClass:"bt-aliasing",attrs:{placeholder:s.$t("message.c105"),maxlength:20},on:{change:s.countFee},model:{value:s.aliasForm.alias,callback:function(a){s.$set(s.aliasForm,"alias",a)},expression:"aliasForm.alias"}})],1),s._v(" "),e("el-form-item",{staticClass:"procedure",attrs:{label:s.$t("message.c28")+": "+this.fee+" NULS"}}),s._v(" "),e("div",{staticClass:"allNuls"},[s._v(s._s(s.$t("message.c171"))+": "+s._s(parseFloat(this.allFee.toString()))+" NULS")]),s._v(" "),e("el-form-item",{staticClass:"aliasing-submit"},[e("el-button",{attrs:{type:"primary",id:"aliasAliasing"},on:{click:function(a){s.aliasingSubmit("aliasForm")}}},[s._v("\n          "+s._s(s.$t("message.confirmButtonText"))+"\n        ")])],1)],1)],1),s._v(" "),e("Password",{ref:"password",attrs:{submitId:s.submitId},on:{toSubmit:s.toSubmit}})],1)},staticRenderFns:[]};var o=e("VU/8")(n,r,!1,function(s){e("ek1l")},null,null);a.default=o.exports},ek1l:function(s,a){}});
//# sourceMappingURL=28.43026eff0340ebef6df7.js.map