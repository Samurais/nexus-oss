/**
 * Sonatype NexusTM [Open Source Version].
 * Copyright � 2008 Sonatype, Inc. All rights reserved.
 * Includes the third-party code listed at ${thirdpartyurl}.
 *
 * This program is licensed to you under Version 3 only of the GNU General
 * Public License as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * Version 3 for more details.
 *
 * You should have received a copy of the GNU General Public License
 * Version 3 along with this program. If not, see http://www.gnu.org/licenses/.
 */
package org.sonatype.nexus.proxy.maven.maven2;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.sonatype.nexus.artifact.GavCalculator;
import org.sonatype.nexus.proxy.ItemNotFoundException;
import org.sonatype.nexus.proxy.maven.ArtifactPackagingMapper;
import org.sonatype.nexus.proxy.maven.LayoutConverterShadowRepository;
import org.sonatype.nexus.proxy.registry.ContentClass;
import org.sonatype.nexus.proxy.repository.ShadowRepository;

/**
 * A shadow repository that transforms M1 content hierarchy of master to M2 layouted shadow.
 * 
 * @author cstamas
 */
@Component( role = ShadowRepository.class, hint = "m1-m2-shadow", instantiationStrategy = "per-lookup" )
public class M2LayoutedM1ShadowRepository
    extends LayoutConverterShadowRepository
{
    /**
     * The ContentClass.
     */
    @Requirement( hint = "maven2" )
    private ContentClass contentClass;

    /**
     * The ContentClass.
     */
    @Requirement( hint = "maven1" )
    private ContentClass masterContentClass;

    /**
     * The artifact packaging mapper.
     */
    @Requirement
    private ArtifactPackagingMapper artifactPackagingMapper;

    /**
     * This repo provides Maven2 content.
     */
    public ContentClass getRepositoryContentClass()
    {
        return contentClass;
    }

    public GavCalculator getGavCalculator()
    {
        return getM2GavCalculator();
    }

    public ArtifactPackagingMapper getArtifactPackagingMapper()
    {
        return artifactPackagingMapper;
    }

    /**
     * This repo needs Maven1 content master.
     */
    public ContentClass getMasterRepositoryContentClass()
    {
        return masterContentClass;
    }

    protected String transformMaster2Shadow( String path )
        throws ItemNotFoundException
    {
        return transformM1toM2( path );
    }

    protected String transformShadow2Master( String path )
        throws ItemNotFoundException
    {
        return transformM2toM1( path );
    }
}
