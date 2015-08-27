/**
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Portions copyright 2014-2015 ForgeRock AS.
 */

/*global define*/

define("org/forgerock/openam/ui/admin/views/realms/authorization/policies/conditions/ManageEnvironmentsView", [
    "jquery",
    "underscore",
    "org/forgerock/openam/ui/admin/views/realms/authorization/policies/conditions/ManageRulesView",
    "org/forgerock/commons/ui/common/util/Constants"
], function ($, _, ManageRulesView, Constants) {
    var ManageEnvironmentsView = ManageRulesView.extend({
        element: "#environmentContainer",
        envEvents: {
            "change .environment-area .operator select": "onSelect",
            "mousedown #operatorEnv_0 li.rule:not(.editing)": "setFocus",
            "mousedown #operatorEnv_0 li.operator:not(.editing)": "setFocus",

            "click    #operatorEnv_0 .rule > .item-button-panel > .fa-trash-o": "onDelete",
            "keyup    #operatorEnv_0 .rule > .item-button-panel > .fa-trash-o": "onDelete",
            "click    #operatorEnv_0 .rule > .item-button-panel > .fa-pencil": "toggleEditing",
            "keyup    #operatorEnv_0 .rule > .item-button-panel > .fa-pencil": "toggleEditing",
            "click    #operatorEnv_0 .rule > .item-button-panel > .fa-check": "toggleEditing",
            "keyup    #operatorEnv_0 .rule > .item-button-panel > .fa-check": "toggleEditing",
            "dblclick #operatorEnv_0 li.rule:not(.legacy-condition)": "toggleEditing"
        },
        data: {},
        buttons: {},
        idCount: 0,
        typeAND: {
            "title": "AND",
            "logical": true,
            "config": {
                "properties": {
                    "conditions": {
                        "type": "array",
                        "items": {
                            "type": "any"
                        }
                    }
                }
            }
        },

        render: function (args, callback, element) {
            this.idPrefix = "Env_";
            this.property = "condition";
            this.properties = "conditions";
            this.data.conditionName = "Environment Condition";
            this.data.entity = args.entity;
            this.data.options = args.options;
            this.data.conditions = [];
            this.data.operators = [];

            var self = this;

            _.each(this.data.options.availableEnvironments, function (item) {

                if (item.logical === true) {
                    self.data.operators.push(item);
                } else {
                    self.data.conditions.push(item);
                }

                delete item.config.type;
            });

            if (!_.findWhere(this.data.operators, {title: "AND"})) {
                this.data.operators.push(this.typeAND);
            }

            this.init(args, this.envEvents);
            this.conditionType = Constants.ENVIRONMENT;
            this.setElement(this.element);

            this.idCount = 0;

            this.parentRender(function () {
                this.buttons.clearBtn = this.$el.find("a#clear");
                this.buttons.addCondition = this.$el.find("a#addCondition");
                this.buttons.addOperator = this.$el.find("a#addOperator");
                this.pickUpItem = this.$el.find("ol#pickUpItem");

                if (self.data.operators.length === 0) {
                    this.buttons.addOperator.hide();
                }

                this.buildList();
                this.onClear();
                this.initSorting();

                if (callback) {
                    callback();
                }
            });
        }
    });

    return new ManageEnvironmentsView();
});