/*
 * Copyright (C) 2017-2019 Dremio Corporation
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
import { PureComponent } from "react";

import Radium from "radium";
import PropTypes from "prop-types";
import { primary } from "uiTheme/radium/buttons";

class Submit extends PureComponent {
  static propTypes = {
    onClick: PropTypes.func,
    children: PropTypes.node,
  };

  constructor(props) {
    super(props);
  }

  render() {
    return (
      <button type="submit" style={primary}>
        {this.props.children}
      </button>
    );
  }
}
export default Radium(Submit);
