/*
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
 * information: "Portions Copyrighted [year] [name of copyright owner]".
 *
 * Copyright 2014 ForgeRock AS. All rights reserved.
 */

package org.forgerock.openam.sts;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;

/**
 * Interface to wrap consumption of the openam-shared XMLUtils class so that direct consumption of static methods can
 * be avoided to facilitate unit testing.
 */
public interface XMLUtilities {
    Document stringToDocumentConversion(String inputString);
    String documentToStringConversion(Node inputNode);
    Document newSafeDocument(boolean schemaValidation) throws ParserConfigurationException;
    public Transformer getNewTransformer() throws TokenMarshalException;
}
