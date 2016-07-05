import React from 'react';

import SimpleTable from './SimpleTable';
import Pagination from 'react-bootstrap/lib/Pagination';

export default class ServerSideTable extends SimpleTable {

  constructor(props) {
    super(props);
    _.extend(this.state, {
      serverPage: 1,
      atEnd: false,
      paginate: props.paginate
    });
  }

  handleSelect(event, selectedEvent) {
    let inc = selectedEvent.eventKey > this.state.serverPage ? 1 : -1;
    this.props.dispatch(this.props.fetchAction.trigger(...this.props.fetchParams, this.props.perPage, this.state.serverPage + inc));
    let state = {
      serverPage: this.state.serverPage + inc
    }
    if (inc < 0) _.extend(state, {atEnd: false});
    this.setState(state);
  }

  updateDisplay(nextProps) {
    let newState = {};
    if (this.props.entries && this.props.entries.length > 0 && nextProps.entries.length == 0 && this.state.serverPage > 1) {
      this.props.dispatch(this.props.fetchAction.trigger(...this.props.fetchParams, this.props.perPage, this.state.serverPage - 1));
      _.extend(newState, {
        serverPage: this.state.serverPage - 1,
        atEnd: true
      });
    } else if (nextProps.entries && nextProps.entries.length > 0) {
      _.extend(newState, {
        displayItems: nextProps.entries
      });
    }

    if (!this.state.paginate && nextProps.paginate) {
      _.extend(newState, {
        paginate: true
      });
    }

    this.setState(newState);
  }

  componentWillReceiveProps(nextProps) {
    this.updateDisplay(nextProps);
  }

  renderPagination() {
    if (this.state.paginate) {
      return (
        <div className="pagination-container">
          <Pagination
            prev={true}
            next={true}
            first={false}
            last={false}
            ellipsis={false}
            items={this.state.atEnd ? this.state.serverPage : this.state.serverPage + 1}
            maxButtons={1}
            activePage={this.state.serverPage}
            onSelect={this.handleSelect.bind(this)} />
        </div>
      );
    }
  }
}

ServerSideTable.propTypes = _.extend({}, SimpleTable.propTypes, {
  fetchAction: React.PropTypes.shape({
      trigger: React.PropTypes.func.isRequired
  }),
  dispatch: React.PropTypes.func.isRequired,
  fetchParams: React.PropTypes.array,
  paginate: React.PropTypes.bool
});