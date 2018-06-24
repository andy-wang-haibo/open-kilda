/* Copyright 2017 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.northbound.service;

import org.openkilda.northbound.dto.LinkPropsDto;
import org.openkilda.northbound.dto.LinksDto;

import java.util.List;

public interface LinkService extends BasicService {

    /**
     * Returns all links at the controller.
     */
    List<LinksDto> getLinks();

    /**
     * These results are not related to the ISL links per se .. they are based on any link
     * properties that have been uploaded through setLinkProps.
     *
     * @param srcSwitch source switch dpid.
     * @param srcPort source port number.
     * @param dstSwitch destination switch dpid.
     * @param dstPort destination port number.
     * @return one or more link properties from the static link_props table.
     */
    List<LinkPropsDto> getLinkProps(String srcSwitch, Integer srcPort, String dstSwitch, Integer dstPort);

    /**
     * All linkPropsList link properties will be created/updated, and pushed to ISL links if they exit.
     *
     * @param linkPropsList the list of link properties to create / update
     * @return the number of successes, failures, and any failure messages
     */
    LinkPropsResult setLinkProps(List<LinkPropsDto> linkPropsList);

    /**
     * All linkPropsList link properties will be deleted, and deleted from ISL links if they exist.
     *
     * @param linkPropsList the list of link properties to delete
     * @return the number of successes (rows affected), failures, and any failure messages
     */
    LinkPropsResult delLinkProps(List<LinkPropsDto> linkPropsList);
}
