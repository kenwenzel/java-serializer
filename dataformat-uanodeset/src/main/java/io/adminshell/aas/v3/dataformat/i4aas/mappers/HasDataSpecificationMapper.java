/*
 * Copyright (c) 2021 Fraunhofer-Gesellschaft zur Foerderung der angewandten Forschung e. V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.adminshell.aas.v3.dataformat.i4aas.mappers;

import java.util.List;

import org.opcfoundation.ua._2011._03.uanodeset.ListOfReferences;
import org.opcfoundation.ua._2011._03.uanodeset.UAObject;

import io.adminshell.aas.v3.dataformat.i4aas.mappers.utils.MappingContext;
import io.adminshell.aas.v3.dataformat.i4aas.mappers.utils.UaIdentifier;
import io.adminshell.aas.v3.model.DataSpecificationContent;
import io.adminshell.aas.v3.model.DataSpecificationIEC61360;
import io.adminshell.aas.v3.model.EmbeddedDataSpecification;
import io.adminshell.aas.v3.model.HasDataSpecification;
import io.adminshell.aas.v3.model.Reference;

public interface HasDataSpecificationMapper {

	public default void mapDataSpecification(HasDataSpecification source, UAObject target, MappingContext ctx) {

		UAObject folder = I4AASMapper.createFolder(target, "DataSpecification", ctx);

		List<EmbeddedDataSpecification> embeddedDataSpecifications = source.getEmbeddedDataSpecifications();
		for (int i = 0; i < embeddedDataSpecifications.size(); i++) {
			EmbeddedDataSpecification embeddedDataSpecification = embeddedDataSpecifications.get(i);
			Reference dataSpecification = embeddedDataSpecification.getDataSpecification();

			if (dataSpecification != null) {
				String dataSpecKey = dataSpecification.getKeys().get(0).getValue();
				UAObject uaObject = new ReferenceMapper(dataSpecification, ctx, dataSpecKey).map();
				I4AASMapper.attachAsComponent(folder, uaObject);
			}

			DataSpecificationContent dataSpecificationContent = embeddedDataSpecification.getDataSpecificationContent();
			if (dataSpecificationContent instanceof DataSpecificationIEC61360) {
				UAObject uaIEC61360 = new DataSpecificationIEC61360Mapper(
						(DataSpecificationIEC61360) dataSpecificationContent, ctx, "embedded_" + i, ctx.getModelNsIndex()).map();
				I4AASMapper.attachAsComponent(folder, uaIEC61360);
			}
		}
	}
}
