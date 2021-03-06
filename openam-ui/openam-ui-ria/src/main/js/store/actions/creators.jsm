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
 * Copyright 2016 ForgeRock AS.
 */

import {
    SERVER_ADD_REALM,
    SESSION_ADD_REALM,
    SESSION_REMOVE_REALM
} from "./types";

export const sessionAddRealm = (absolutePath) => ({
    type: SESSION_ADD_REALM,
    absolutePath: absolutePath.toLowerCase()
});

export const sessionRemoveRealm = () => ({
    type: SESSION_REMOVE_REALM
});

export const serverAddRealm = (realm) => ({
    type: SERVER_ADD_REALM,
    realm: realm.toLowerCase()
});
